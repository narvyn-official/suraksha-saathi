import { db, access, audit, failure, json, digest } from "@/lib/server";
import { validateImport } from "@/lib/grading";
export async function POST(request: Request) {
  try {
    const actor = await access("write"), who = actor.owner;
    const data = validateImport(await json(request));
    const inserts = [];
    for (const a of data.attempts) {
      inserts.push(db().prepare("INSERT INTO attempts(owner,id,worker_id,worker_name,payload,digest,imported_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(owner,id) DO NOTHING")
        .bind(who,a.id,data.worker.id,data.worker.name,JSON.stringify(a),await digest(a),Date.now()));
      inserts.push(audit(actor,"assessment.import",a.id,{workerId:data.worker.id},true));
    }
    inserts.push(
      db()
        .prepare(
          "INSERT INTO workers(owner,id,name,sector,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(owner,id) DO UPDATE SET name=excluded.name,sector=excluded.sector,updated_at=excluded.updated_at",
        )
        .bind(
          who,
          data.worker.id,
          data.worker.name,
          data.worker.sector,
          Date.now(),
        ),
    );
    const results=await db().batch(inserts);
    const imported=data.attempts.reduce((sum,_,i)=>sum+Number(results[i*2].meta.changes),0);
    const unchanged=data.attempts.length-imported;
    return Response.json({ imported, unchanged });
  } catch (e) {
    if(e instanceof Error && e.message.includes("Record conflict:"))return failure(new Error("Record conflict: an existing attempt has different answers."));
    return failure(e);
  }
}
