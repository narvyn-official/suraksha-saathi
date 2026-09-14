import { db, owner, failure, json, digest } from "@/lib/server";
import { validateImport } from "@/lib/grading";
export async function POST(request: Request) {
  try {
    const who = await owner();
    const data = validateImport(await json(request));
    const inserts = [];
    let unchanged = 0;
    for (const a of data.attempts) {
      const hash = await digest(a);
      const existing = await db()
        .prepare("SELECT digest FROM attempts WHERE owner=? AND id=?")
        .bind(who, a.id)
        .first<{ digest: string }>();
      if (existing) {
        if (existing.digest !== hash)
          throw new Error(
            "Record conflict: an existing attempt has different answers.",
          );
        unchanged++;
        continue;
      }
      inserts.push(
        db()
          .prepare(
            "INSERT INTO attempts(owner,id,worker_id,worker_name,payload,digest,imported_at) VALUES(?,?,?,?,?,?,?)",
          )
          .bind(
            who,
            a.id,
            data.worker.id,
            data.worker.name,
            JSON.stringify(a),
            hash,
            Date.now(),
          ),
      );
    }
    const imported = inserts.length;
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
    await db().batch(inserts);
    return Response.json({ imported, unchanged });
  } catch (e) {
    return failure(e);
  }
}
