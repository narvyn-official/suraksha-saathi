import { db, owner, access, audit, failure, json, signingKey } from "@/lib/server";
import { sign, trust, credentialView } from "@/lib/credentials";
import { requestedExpiry } from "@/lib/validity";
export async function POST(request: Request) {
  try {
    const actor = await access("write"), who = actor.owner,
      input = await json(request);
    if (typeof input.attemptId !== "string")
      throw new Error("Invalid attempt.");
    const row = await db()
      .prepare("SELECT payload FROM attempts WHERE owner=? AND id=?")
      .bind(who, input.attemptId)
      .first<{ payload: string }>();
    if (!row) throw new Error("No training record found.");
    const a = JSON.parse(row.payload);
    if (a.kind !== "assessment" || !a.result.passed)
      throw new Error(
        "Only passed assessments can receive a pilot credential.",
      );
    const prior = await db()
      .prepare(
        "SELECT id,attempt_id,token,issued_at,revoked_at,reason FROM credentials WHERE owner=? AND attempt_id=?",
      )
      .bind(who, input.attemptId)
      .first<{ id: string; attempt_id: string; token: string; issued_at: number; revoked_at: number | null; reason: string | null }>();
    if (prior) {
      const saved = credentialView(prior);
      if (input.expiresAt !== undefined && input.expiresAt !== saved.expiresAt)
        throw new Error("Invalid renewal: this assessment already has a credential. Renewal requires a new passed assessment.");
      return Response.json(saved);
    }
    const id = crypto.randomUUID(),
      now = Date.now();
    const expiresAt = requestedExpiry(input.expiresAt, now);
    const token = await sign(
      {
        iss: trust.issuer,
        id,
        attemptId: a.id,
        workerRef: a.workerId,
        moduleId: a.moduleId,
        contentVersion: a.contentVersion,
        score: a.result.score,
        kind: "pilot-simulation",
        mode: a.mode,
        practical: "not-assessed",
        iat: now,
        expiresAt,
      },
      signingKey(),
    );
    await db().batch([db()
      .prepare(
        "INSERT INTO credentials(owner,id,attempt_id,token,issued_at) VALUES(?,?,?,?,?)",
      )
      .bind(who, id, a.id, token, now),audit(actor,"credential.issue",id,{attemptId:a.id,expiresAt})]);
    return Response.json(credentialView({ id, token, attempt_id: a.id, issued_at: now, revoked_at: null, reason: null }));
  } catch (e) {
    return failure(e);
  }
}
export async function PATCH(request: Request) {
  try {
    const actor = await access("write"), who = actor.owner,
      input = await json(request);
    if (
      typeof input.id !== "string" ||
      typeof input.reason !== "string" ||
      input.reason.trim().length < 5 ||
      input.reason.length > 500
    )
      throw new Error("Invalid revocation reason. Use 5–500 characters.");
    const changes = await db().batch([db()
      .prepare(
        "UPDATE credentials SET revoked_at=?,reason=? WHERE owner=? AND id=? AND revoked_at IS NULL",
      )
      .bind(Date.now(), input.reason.trim(), who, input.id),audit(actor,"credential.revoke",input.id,{reason:input.reason.trim()},true)]);
    const change=changes[0];
    if (!change.meta.changes) throw new Error("No active credential found.");
    return Response.json({ revoked: true });
  } catch (e) {
    return failure(e);
  }
}
