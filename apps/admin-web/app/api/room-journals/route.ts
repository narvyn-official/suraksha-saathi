import { db, owner, access, audit, failure, digest } from "@/lib/server";
import { assertRoomSuccessor, readRoomImport, validateRoomJournal } from "@/lib/room-journals";

export async function POST(request: Request) {
  try {
    const actor = await access("write"), who = actor.owner, data = await readRoomImport(request);
    const statements: D1PreparedStatement[] = [];
    let imported = 0, unchanged = 0;
    for (const record of data.rooms) {
      const hash = await digest(record);
      const known = await db().prepare("SELECT digest FROM room_journal_snapshots WHERE owner=? AND id=? AND digest=?").bind(who, record.id, hash).first();
      if (known) { unchanged++; continue; }
      const previous = await db().prepare("SELECT digest,payload FROM room_journal_heads WHERE owner=? AND id=?").bind(who, record.id).first<{ digest: string; payload: string }>();
      if (previous) assertRoomSuccessor(validateRoomJournal(JSON.parse(previous.payload), data.worker.id), record);
      const payload = JSON.stringify(record), importedAt = Date.now();
      statements.push(db().prepare("INSERT INTO room_journal_snapshots(owner,id,digest,previous_digest,captured_at,imported_at,payload) VALUES(?,?,?,?,?,?,?)")
        .bind(who, record.id, hash, previous?.digest ?? null, record.updatedAt, importedAt, payload));
      statements.push(db().prepare("INSERT INTO room_journal_heads(owner,id,worker_id,worker_name,worker_sector,digest,captured_at,payload,imported_at) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(owner,id) DO UPDATE SET worker_name=excluded.worker_name,worker_sector=excluded.worker_sector,digest=excluded.digest,captured_at=excluded.captured_at,payload=excluded.payload,imported_at=excluded.imported_at")
        .bind(who, record.id, record.workerId, data.worker.name, data.worker.sector, hash, record.updatedAt, payload, importedAt));
      imported++;
    }
    // D1 batch is atomic. The insertion trigger rejects stale parents, including racing imports.
    if (statements.length) { statements.push(audit(actor,"room.import",data.worker.id,{imported,unchanged}));await db().batch(statements); }
    return Response.json({ imported, unchanged, omittedCount: data.omittedCount ?? 0, certifiable: false });
  } catch (e) { return failure(e); }
}

export async function GET(request: Request) {
  try {
    const who = await owner(), url = new URL(request.url), attempt = url.searchParams.get("attempt");
    if (attempt) {
      if (!/^[0-9a-f-]{36}$/i.test(attempt)) throw new Error("Invalid room attempt ID.");
      const data = await db().prepare("SELECT digest,captured_at,imported_at,payload FROM room_journal_snapshots WHERE owner=? AND id=? ORDER BY captured_at DESC,digest DESC LIMIT 100")
        .bind(who, attempt).all<{ digest: string; captured_at: number; imported_at: number; payload: string }>();
      const total = await db().prepare("SELECT COUNT(*) AS n FROM room_journal_snapshots WHERE owner=? AND id=?").bind(who, attempt).first<{ n: number }>();
      return Response.json({ snapshots: data.results.map(row => ({ ...row, payload: JSON.parse(row.payload) })), total: total?.n ?? 0, certifiable: false }, { headers: { "Cache-Control": "no-store" } });
    }
    const data = await db().prepare("SELECT h.*,(SELECT COUNT(*) FROM room_journal_snapshots s WHERE s.owner=h.owner AND s.id=h.id) AS snapshots FROM room_journal_heads h WHERE h.owner=? ORDER BY h.captured_at DESC,h.id DESC LIMIT 500")
      .bind(who).all<{ payload: string }>();
    const count = await db().prepare("SELECT COUNT(*) AS n FROM room_journal_heads WHERE owner=?").bind(who).first<{ n: number }>();
    return Response.json({ rooms: data.results.map(row => ({ ...row, payload: JSON.parse(row.payload) })), coverage: { returned: data.results.length, total: count?.n ?? 0, truncated: (count?.n ?? 0) > data.results.length }, certifiable: false }, { headers: { "Cache-Control": "no-store" } });
  } catch (e) { return failure(e); }
}
