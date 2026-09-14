import { db, owner, failure } from "@/lib/server";
export async function GET() {
  try {
    const who = await owner();
    const records = await db()
      .prepare(
        "SELECT a.id,a.worker_id,COALESCE(w.name,a.worker_name) AS worker_name,COALESCE(w.sector,'Unspecified') AS worker_sector,a.payload,a.imported_at FROM attempts a LEFT JOIN workers w ON w.owner=a.owner AND w.id=a.worker_id WHERE a.owner=? ORDER BY a.imported_at DESC,a.id DESC LIMIT 500",
      )
      .bind(who)
      .all();
    const certificates = await db()
      .prepare(
        "SELECT id,attempt_id,token,issued_at,revoked_at,reason FROM credentials WHERE owner=? ORDER BY issued_at DESC LIMIT 500",
      )
      .bind(who)
      .all();
    const total = await db()
      .prepare("SELECT COUNT(*) AS n FROM attempts WHERE owner=?")
      .bind(who)
      .first<{ n: number }>();
    return Response.json(
      {
        attempts: records.results.map((r: any) => ({
          ...r,
          payload: JSON.parse(r.payload),
        })),
        credentials: certificates.results,
        coverage: {
          returned: records.results.length,
          total: total?.n ?? 0,
          truncated: (total?.n ?? 0) > records.results.length,
        },
      },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (e) {
    return failure(e);
  }
}
