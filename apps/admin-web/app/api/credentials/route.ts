import { db, owner, failure, json, signingKey } from "@/lib/server";
import { sign, trust } from "@/lib/credentials";
export async function POST(request: Request) {
  try {
    const who = await owner(),
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
        "SELECT id,token,revoked_at FROM credentials WHERE owner=? AND attempt_id=?",
      )
      .bind(who, input.attemptId)
      .first();
    if (prior) return Response.json(prior);
    const id = crypto.randomUUID(),
      now = Date.now();
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
      },
      signingKey(),
    );
    await db()
      .prepare(
        "INSERT INTO credentials(owner,id,attempt_id,token,issued_at) VALUES(?,?,?,?,?)",
      )
      .bind(who, id, a.id, token, now)
      .run();
    return Response.json({ id, token, revoked_at: null });
  } catch (e) {
    return failure(e);
  }
}
export async function PATCH(request: Request) {
  try {
    const who = await owner(),
      input = await json(request);
    if (
      typeof input.id !== "string" ||
      typeof input.reason !== "string" ||
      input.reason.trim().length < 5 ||
      input.reason.length > 500
    )
      throw new Error("Invalid revocation reason. Use 5–500 characters.");
    const change = await db()
      .prepare(
        "UPDATE credentials SET revoked_at=?,reason=? WHERE owner=? AND id=? AND revoked_at IS NULL",
      )
      .bind(Date.now(), input.reason.trim(), who, input.id)
      .run();
    if (!change.meta.changes) throw new Error("No active credential found.");
    return Response.json({ revoked: true });
  } catch (e) {
    return failure(e);
  }
}
