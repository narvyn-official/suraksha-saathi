import { account, certifierFor } from "./auth-client.mjs";
const identity=await account({approved:true});
const certifier=await certifierFor(identity);
import assert from "node:assert/strict";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { randomUUID } from "node:crypto";
const curriculum = JSON.parse(
  readFileSync(new URL("../lib/curriculum.json", import.meta.url)),
);
async function call(path, body, method = "POST", auth = true, cookie = identity.cookie) {
  const r = await fetch("http://localhost:5173/api/" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(auth ? { Cookie: cookie } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: r.status, data: await r.json() };
}
// Run the full request/review contract with two independent authenticated accounts.
async function issue(body) {
 const request=await call('credentials',{action:'request',note:'Synthetic evidence reviewed for regression testing',...body});
 if(request.status!==202)return request;
 return call('credentials',{action:'approve',requestId:request.data.request.id,reason:'Independent synthetic assessment review completed'},'POST',true,certifier.cookie);
}
assert.equal((await call("records", undefined, "GET", false)).status, 401);
const testExpiry = Date.now() + 7 * 24 * 60 * 60 * 1000; // Test fixture only.
const legacyToken = readFileSync(new URL("../../android/app/src/androidTest/assets/legacy-no-expiry-credential.txt", import.meta.url), "utf8");
const legacyVerified = await call("verify", { token: legacyToken });
assert.equal(legacyVerified.status, 200);
assert.equal(legacyVerified.data.signatureValid, true);
assert.equal(legacyVerified.data.expiryStatus, "not-recorded");
assert.notEqual(legacyVerified.data.status, "active");
const now = Date.now(),
  worker = {
    id: randomUUID(),
    name: "Demo learner · test record",
    sector: "Mining",
  };
const attempts = curriculum.modules.map((m) => ({
  id: randomUUID(),
  workerId: worker.id,
  moduleId: m.id,
  contentVersion: curriculum.version,
  kind: "assessment",
  mode: "screen",
  finished: true,
  startedAt: now,
  endedAt: now + 9000,
  events: m.questions.map((q, j) => ({
    type: "answer",
    sequence: j + 1,
    questionId: q.id,
    optionId: q.options.find((o) => o.correct).id,
    time: now + (j + 1) * 1000,
  })),
  result: { score: 2, passed: false },
}));
const batch = { schemaVersion: 1, worker, attempts };
const first = await call("import", batch);
assert.equal(first.status, 200, JSON.stringify(first));
assert.equal(first.data.imported, attempts.length);
assert.equal((await call("import", batch)).data.unchanged, attempts.length);
const rows = (await call("records", undefined, "GET")).data.attempts;
assert.equal(
  rows.find((r) => r.id === attempts[0].id).payload.result.score,
  100,
);
assert.equal(rows.find((r) => r.id === attempts[0].id).worker_sector, "Mining");
for (const invalidExpiry of [undefined, null, "invalid", testExpiry + .5, 0, now - 1, 8_640_000_000_000_001]) {
  assert.equal((await issue({ attemptId: attempts[0].id, expiresAt: invalidExpiry })).status, 400);
}
const machinery = await issue({ attemptId: attempts[2].id, expiresAt: testExpiry });
assert.equal(machinery.status, 200);
assert.equal(
  (await call("verify", { token: machinery.data.token })).data.moduleId,
  "machinery",
);
const ppeAttempt = attempts.find((a) => a.moduleId === "ppe");
const ppeCertificate = await issue({ attemptId: ppeAttempt.id, expiresAt: testExpiry });
assert.equal(ppeCertificate.status, 200);
assert.equal(
  (await call("verify", { token: ppeCertificate.data.token })).data.moduleId,
  "ppe",
);
const emergencyAttempt = attempts.find((a) => a.moduleId === "emergency");
assert.ok(emergencyAttempt, "Emergency assessment fixture is required");
const emergencyCertificate = await issue({
  attemptId: emergencyAttempt.id,
  expiresAt: testExpiry,
});
assert.equal(
  emergencyCertificate.status,
  200,
  JSON.stringify(emergencyCertificate),
);
const verifiedEmergency = await call("verify", {
  token: emergencyCertificate.data.token,
});
assert.equal(verifiedEmergency.status, 200);
assert.equal(verifiedEmergency.data.moduleId, "emergency");
assert.equal(verifiedEmergency.data.status, "active");
const v03 = structuredClone(batch);
v03.attempts = v03.attempts
  .filter((a) => ["fire", "gas", "machinery", "ppe"].includes(a.moduleId))
  .map((a) => ({ ...a, id: randomUUID(), contentVersion: "0.3.0", endedAt: now - 1000, startedAt: now-10000, events:a.events.map(e=>({...e,time:e.time-10000})) }));
const importedV03 = await call("import", v03);
assert.equal(importedV03.status, 200, JSON.stringify(importedV03));
assert.equal(importedV03.data.imported, 4);
const falseEmergencyVersion = structuredClone(batch);
falseEmergencyVersion.attempts = [
  { ...emergencyAttempt, id: randomUUID(), contentVersion: "0.3.0" },
];
assert.equal((await call("import", falseEmergencyVersion)).status, 400);
const previous = structuredClone(batch);
previous.attempts = previous.attempts
  .filter((a) => ["fire", "gas", "machinery"].includes(a.moduleId))
  .map((a) => ({ ...a, id: randomUUID(), contentVersion: "0.2.0", endedAt: now - 1000, startedAt: now-10000, events:a.events.map(e=>({...e,time:e.time-10000})) }));
const importedV02 = await call("import", previous);
assert.equal(importedV02.status, 200, JSON.stringify(importedV02));
assert.equal(importedV02.data.imported, 3);
const falseVersion = structuredClone(batch);
falseVersion.attempts = [
  { ...ppeAttempt, id: randomUUID(), contentVersion: "0.2.0" },
];
assert.equal((await call("import", falseVersion)).status, 400);
const old = structuredClone(batch);
old.attempts = old.attempts
  .filter((a) => ["fire", "gas"].includes(a.moduleId))
  .map((a) => ({ ...a, id: randomUUID(), contentVersion: "0.1.0", endedAt: now - 1000, startedAt: now-10000, events:a.events.map(e=>({...e,time:e.time-10000})) }));
assert.equal((await call("import", old)).status, 200);
const unsupported = structuredClone(batch);
unsupported.attempts = [
  { ...unsupported.attempts[2], id: randomUUID(), contentVersion: "0.1.0" },
];
assert.equal((await call("import", unsupported)).status, 400);
const failed = structuredClone(batch);
failed.worker = {
  id: randomUUID(),
  name: "Demo retraining · test record",
  sector: "Steel",
};
failed.attempts = [
  {
    ...failed.attempts[2],
    id: randomUUID(),
    workerId: failed.worker.id,
    events: [
      {
        ...failed.attempts[2].events[0],
        optionId: curriculum.modules[2].questions[0].options.find(
          (o) => !o.correct,
        ).id,
      },
    ],
  },
];
assert.equal((await call("import", failed)).status, 200);
assert.equal(
  (await issue({ attemptId: failed.attempts[0].id, expiresAt: testExpiry })).status,
  400,
);
const emergencyModule = curriculum.modules.find((m) => m.id === "emergency");
const criticalIndex = emergencyModule.questions.findIndex((q) => q.critical);
assert.ok(criticalIndex >= 0);
const failedEmergency = structuredClone(batch);
failedEmergency.worker = {
  id: randomUUID(),
  name: "Demo emergency retraining · test record",
  sector: "Mica",
};
failedEmergency.attempts = [
  {
    ...emergencyAttempt,
    id: randomUUID(),
    workerId: failedEmergency.worker.id,
    events: emergencyAttempt.events.slice(0, criticalIndex + 1).map((e, i) => ({
      ...e,
      optionId:
        i === criticalIndex
          ? emergencyModule.questions[i].options.find((o) => !o.correct).id
          : e.optionId,
    })),
  },
];
const importedFailedEmergency = await call("import", failedEmergency);
assert.equal(
  importedFailedEmergency.status,
  200,
  JSON.stringify(importedFailedEmergency),
);
assert.equal(
  (await issue({ attemptId: failedEmergency.attempts[0].id, expiresAt: testExpiry }))
    .status,
  400,
);
const altered = structuredClone(batch);
altered.attempts[0].mode = "arcore";
assert.equal((await call("import", altered)).status, 400);
const invalid = structuredClone(batch);
invalid.attempts[0].id = randomUUID();
invalid.attempts[0].events[0].optionId = "invented";
assert.equal((await call("import", invalid)).status, 400);
const cert = await issue({ attemptId: attempts[0].id, expiresAt: testExpiry });
assert.equal(cert.status, 200, JSON.stringify(cert));
assert.equal(cert.data.expiresAt, testExpiry);
assert.equal(cert.data.expiryStatus, "within-validity");
assert.equal((await issue({ attemptId: attempts[0].id, expiresAt: testExpiry + 1000 })).status, 400);
assert.equal(
  (await issue({ attemptId: attempts[0].id, expiresAt: testExpiry })).status,
  400, // Renewal requires a fresh assessment; no direct signing retry path.
);
assert.equal(
  (await call("verify", { token: cert.data.token })).data.status,
  "active",
);
const parts = cert.data.token.split(".");
const p = JSON.parse(Buffer.from(parts[1], "base64url"));
p.expiresAt = testExpiry + 86_400_000;
parts[1] = Buffer.from(JSON.stringify(p)).toString("base64url");
assert.equal((await call("verify", { token: parts.join(".") })).status, 400);
assert.equal(
  (
    await call(
      "credentials",
      { id: cert.data.id, reason: "Integration test revocation" },
      "PATCH",
    )
  ).status,
  200,
);
assert.equal(
  (await call("verify", { token: cert.data.token })).data.status,
  "revoked",
);
const active = await issue({ attemptId: attempts[1].id, expiresAt: testExpiry });
assert.equal(active.status, 200);
mkdirSync(new URL("../../../artifacts", import.meta.url), { recursive: true });
writeFileSync(
  new URL("../../../artifacts/demo-training-record.json", import.meta.url),
  JSON.stringify(batch, null, 2),
);
writeFileSync(
  new URL("../../../artifacts/demo-credential.txt", import.meta.url),
  "SURAKSHA:CREDENTIAL:" + active.data.token,
);
writeFileSync(
  new URL("../../../artifacts/demo-emergency-credential.txt", import.meta.url),
  "SURAKSHA:CREDENTIAL:" + emergencyCertificate.data.token,
);
console.log(
  "PASS: five-domain import, 0.1/0.2/0.3 archive compatibility, false-version rejection, emergency critical gate and signed verification, unauthenticated rejection, idempotency, score replay, conflict rejection, invalid answers, issuance, signature tamper and revocation. Demo records remain for preview.",
);
