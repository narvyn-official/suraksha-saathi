import { db, owner, failure, json } from "@/lib/server";
import { verify } from "@/lib/credentials";
export async function POST(request: Request) {
  try {
    const who = await owner(),
      input = await json(request);
    const result = await verify(input.token);
    const row = await db()
      .prepare(
        "SELECT revoked_at,reason,token FROM credentials WHERE owner=? AND id=?",
      )
      .bind(who, result.payload.id)
      .first<{
        revoked_at: number | null;
        reason: string | null;
        token: string;
      }>();
    if (!row)
      return Response.json({
        ...result.payload,
        signatureValid: true,
        status: "signature-only",
        message:
          "Signature verified. This workspace cannot confirm current revocation status.",
      });
    if (row.token !== result.token)
      throw new Error("Invalid credential record.");
    return Response.json({
      ...result.payload,
      signatureValid: true,
      status: row.revoked_at ? "revoked" : "active",
      reason: row.reason,
    });
  } catch (e) {
    return failure(e);
  }
}
