import { account } from "./auth-client.mjs";
const identity=await account();
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { roomFixture, roomExport } from "./room-fixtures";
async function call(path: string, body?: unknown, auth = true) {
  const result = await fetch(`http://localhost:5173/api/${path}`, { method: body ? "POST" : "GET", headers: { "Content-Type": "application/json", ...(auth ? { Cookie: identity.cookie } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: result.status, data: await result.json() as any };
}
assert.equal((await call("room-journals", undefined, false)).status, 401);
const before = (await call("records")).data;
const initial = roomFixture(2, "fire", false), complete = roomFixture();
initial.id = randomUUID();complete.id = initial.id;
const exportOf = (records: typeof initial[]) => ({ ...roomExport(records), worker: { ...roomExport(records).worker, name: "Demo room journal · integration" } });
const first = await call("room-journals", exportOf([initial]));assert.equal(first.status, 200, JSON.stringify(first));assert.equal(first.data.imported, 1);
assert.equal((await call("room-journals", exportOf([initial]))).data.unchanged, 1);
assert.equal((await call("room-journals", exportOf([complete]))).data.imported, 1);
assert.equal((await call("room-journals", exportOf([initial]))).data.unchanged, 1, "Old snapshots remain idempotent after completion");
const history = await call(`room-journals?attempt=${initial.id}`);assert.equal(history.data.total, 2);assert.equal(history.data.snapshots[0].payload.mission.completed, true);assert.equal(history.data.snapshots[1].payload.mission.completed, false);
const oldChanged = structuredClone(complete);oldChanged.updatedAt++;oldChanged.mission.events[0].at++;
const newGas = roomFixture(2, "gas");newGas.id = randomUUID();
assert.equal((await call("room-journals", exportOf([newGas, oldChanged]))).status, 400);
assert.equal((await call(`room-journals?attempt=${newGas.id}`)).data.total, 0, "Conflicting import must be all-or-nothing");
const changedOwner = structuredClone(complete);changedOwner.workerId = randomUUID();const ownerExport = exportOf([changedOwner]);ownerExport.worker.id = changedOwner.workerId;
assert.equal((await call("room-journals", ownerExport)).status, 400);
const forged = structuredClone(complete);forged.id = randomUUID();(forged.mission.result as any).certifiable = true;
assert.equal((await call("room-journals", exportOf([forged]))).status, 400);
assert.equal((await call("room-journals", { ...exportOf([complete]), padding: "x".repeat(1_000_001) })).status, 400);
assert.equal((await call("credentials", { attemptId: initial.id, expiresAt: Date.now() + 86400000 })).status, 400, "Room journals cannot issue credentials");
assert.equal((await call("import", exportOf([complete]))).status, 400, "Assessment importer must reject room journals");
assert.equal((await call("room-journals", exportOf([newGas]))).status, 200);
for (const module of ["fire", "gas"] as const) { const legacy = roomFixture(1, module);legacy.id = randomUUID();legacy.mode = "screen";delete legacy.coaching;assert.equal((await call("room-journals", exportOf([legacy]))).status, 200); }
const list = (await call("room-journals")).data.rooms;assert.equal(list.find((row: any) => row.id === initial.id).snapshots, 2);
const after = (await call("records")).data;assert.deepEqual(after.attempts, before.attempts);const storedCredentials = (rows: any[]) => rows.map(({ id, attempt_id, token, issued_at, expiresAt, revoked_at, reason }) => ({ id, attempt_id, token, issued_at, expiresAt, revoked_at, reason }));assert.deepEqual(storedCredentials(after.credentials), storedCredentials(before.credentials));
console.log("Room journal API integration passed: auth, validation, append-only snapshots, idempotency, atomic conflict, legacy import, credential separation.");
