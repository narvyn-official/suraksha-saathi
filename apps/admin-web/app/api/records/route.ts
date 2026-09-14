import { db, owner, failure } from "@/lib/server";
export async function GET() {
  try {
    const who = await owner();
    const records = await db()
      .prepare(
        "SELECT id,worker_id,worker_name,payload,imported_at FROM attempts WHERE owner=? ORDER BY imported_at DESC LIMIT 500",
      )
      .bind(who)
      .all();
    const certificates = await db()
      .prepare(
        "SELECT id,attempt_id,token,issued_at,revoked_at,reason FROM credentials WHERE owner=? ORDER BY issued_at DESC LIMIT 500",
      )
      .bind(who)
      .all();
    return Response.json(
      {
        attempts: records.results.map((r: any) => ({
          ...r,
          payload: JSON.parse(r.payload),
        })),
        credentials: certificates.results,
      },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (e) {
    return failure(e);
  }
}
