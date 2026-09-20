import { db, owner, failure } from "@/lib/server";
import { credentialView } from "@/lib/credentials";
export async function GET(request:Request) {
  try {
    const who = await owner();
    const attemptId=new URL(request.url).searchParams.get('attemptId');
    if(attemptId){
      if(!/^[0-9a-f-]{36}$/i.test(attemptId))throw new Error('Invalid assessment reference.');
      const row=await db().prepare("SELECT a.id,a.worker_id,COALESCE(w.name,a.worker_name) AS worker_name,COALESCE(w.sector,'Unspecified') AS worker_sector,a.payload FROM attempts a LEFT JOIN workers w ON w.owner=a.owner AND w.id=a.worker_id WHERE a.owner=? AND a.id=?").bind(who,attemptId).first();
      if(!row)throw new Error('No assessment found.');return Response.json({...row,payload:JSON.parse(String(row.payload))},{headers:{'Cache-Control':'no-store'}});
    }
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
      .all<{id:string;attempt_id:string;token:string;issued_at:number;revoked_at:number|null;reason:string|null}>();
    const total = await db()
      .prepare("SELECT COUNT(*) AS n FROM attempts WHERE owner=?")
      .bind(who)
      .first<{ n: number }>();
    return Response.json(
      {
        attempts: records.results.map((r) => ({
          ...r,
          payload: JSON.parse(String(r.payload)),
        })),
        credentials: certificates.results.map(row => credentialView(row)),
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
