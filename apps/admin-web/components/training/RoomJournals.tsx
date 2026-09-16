"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";
import type { RoomJournal, RoomJournalRow } from "@/lib/room-journals";

type Snapshot = { digest: string; captured_at: number; payload: RoomJournal };
const readable = (value: string) => value.toLowerCase().replaceAll(/[-_]/g, " ");
type Listing = { rooms: RoomJournalRow[]; coverage: { total: number } };
type Imported = { imported: number; unchanged: number; omittedCount: number };
type History = { snapshots: Snapshot[]; total: number };
async function api<T>(method = "GET", body?: unknown, query = "") {
  const response = await fetch(`/api/room-journals${query}`, { method, headers: body ? { "Content-Type": "application/json" } : undefined, body: body ? JSON.stringify(body) : undefined });
  const result = await response.json() as T & { error?: string };
  if (!response.ok) throw new Error(result.error ?? "Could not load practice journals.");
  return result;
}
export function RoomJournals({writable=true}:{writable?:boolean}) {
  const [rows, setRows] = useState<RoomJournalRow[]>([]);
  const [total, setTotal] = useState(0), [filter, setFilter] = useState("");
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(""), [message, setMessage] = useState("");
  const [selected, setSelected] = useState<RoomJournalRow | null>(null);
  const [snapshot, setSnapshot] = useState<RoomJournal | null>(null);
  const [history, setHistory] = useState<Snapshot[]>([]), [historyTotal, setHistoryTotal] = useState(0);
  const [historyError, setHistoryError] = useState("");
  const request = useRef(0);
  const load = useCallback(async () => { const data = await api<Listing>();setRows(data.rooms);setTotal(data.coverage.total); }, []);
  useEffect(() => { let alive = true;api<Listing>().then(data => { if (alive) { setRows(data.rooms);setTotal(data.coverage.total); } }).catch(e => { if (alive) setError(e.message); }).finally(() => { if (alive) setLoading(false); });return () => { alive = false; }; }, []);
  async function importFile(file?: File) {
    if (!file) return;
    setBusy(true);setError("");setMessage("");
    try {
      if (file.size > 1_000_000) throw new Error("Choose a room-practice JSON export smaller than 1 MB.");
      const result = await api<Imported>("POST", JSON.parse(await file.text()));await load();
      setMessage(`${result.imported} new snapshot(s) saved; ${result.unchanged} already present.${result.omittedCount ? ` The phone excluded ${result.omittedCount} older journal(s) from this bounded export.` : ""} These records cannot issue certificates.`);
    } catch (e) { setError(e instanceof Error ? e.message : "Import failed."); } finally { setBusy(false); }
  }
  async function review(row: RoomJournalRow) {
    const serial = ++request.current;setSelected(row);setSnapshot(row.payload);setHistory([]);setHistoryTotal(0);setHistoryError("");
    try { const data = await api<History>("GET", undefined, `?attempt=${encodeURIComponent(row.id)}`);if (serial === request.current) { setHistory(data.snapshots);setHistoryTotal(data.total); } }
    catch (e) { if (serial === request.current) setHistoryError(e instanceof Error ? e.message : "Could not load earlier snapshots."); }
  }
  const matches = rows.filter(row => `${row.worker_name} ${row.worker_id} ${row.payload.module} ${row.payload.mode}`.toLowerCase().includes(filter.toLowerCase()));
  return <section className="panel p-6" aria-labelledby="room-journal-title">
    <div className="section-heading"><h2 id="room-journal-title">Room practice journals</h2><span className="count">{rows.length}</span></div>
    <p className="mb-4 text-sm text-slate-600">Unverified virtual practice, separate from assessments and credentials. Camera/screen mode and actions are reported by the phone; imported journals do not prove tracking quality, identity or practical competence.</p>
    <div className="mb-5 rounded-xl bg-violet-50 p-4">
      <label htmlFor="room-journal-import" className="mb-2 block font-medium">Import room practice from Android</label>
      <p className="mb-3 text-sm">On the phone, open My record → Export room practice journals. Incomplete attempts are welcome. Newer snapshots append to the same attempt; saved actions and hints cannot be rewritten. Limits: 100 attempts per file, 512 events per attempt, 1 MB per file.</p>
      <Input id="room-journal-import" type="file" accept="application/json,.json" disabled={busy||!writable} onChange={e => { const file = e.target.files?.[0];e.target.value = "";void importFile(file); }} />
    </div>
    {error && <p role="alert" className="notice error">{error} <Button variant="outline" onClick={() => { setError("");void load().catch(e => setError(e.message)); }}>Retry loading</Button></p>}
    {message && <p role="status" className="notice success">{message}</p>}
    {total > rows.length && <p className="notice">Showing the latest {rows.length} of {total} practice attempts. Earlier snapshots remain stored; this list is limited to 500 attempts.</p>}
    <Input aria-label="Find room practice by worker, module or mode" placeholder="Find worker, fire, gas, camera or screen" value={filter} onChange={e => setFilter(e.target.value)} className="mb-4" />
    {loading ? <p role="status">Loading practice journals…</p> : matches.length === 0 ? <p className="py-8 text-slate-600">{rows.length ? "No matching practice journals." : "No room practice imported yet. Assessment records appear in their own tab."}</p> :
      <Table><TableHeader><TableRow><TableHead>Learner / mission</TableHead><TableHead>Reported practice</TableHead><TableHead>Snapshot</TableHead><TableHead>Review</TableHead></TableRow></TableHeader><TableBody>
        {matches.map(row => <TableRow key={row.id}><TableCell><strong>{row.worker_name || "Unnamed learner"}</strong><span className="table-sub">{row.payload.module === "fire" ? "Fire & evacuation" : "Gas / outside-role protocol"}</span><span className="table-meta">{row.worker_sector} · {row.worker_id}</span></TableCell>
          <TableCell><span className="badge review">{row.payload.mission.completed ? "Virtual journey complete" : "Incomplete practice"}</span><span className="table-sub">{row.payload.mode === "camera" ? "Camera reported" : "Screen"} · {row.payload.coaching?.mode === "recall" ? "Recall" : "Guided"} · {row.payload.coaching?.cues.length ?? 0} requested hints</span></TableCell>
          <TableCell>{new Date(row.captured_at).toLocaleString()}<span className="table-meta">Engine v{row.payload.mission.version} · {row.snapshots} saved snapshot(s)</span></TableCell><TableCell><Button variant="outline" onClick={() => void review(row)}>Read journal</Button></TableCell></TableRow>)}
      </TableBody></Table>}
    <Dialog open={!!selected} onOpenChange={open => { if (!open) { request.current++;setSelected(null);setSnapshot(null); } }}><DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl"><DialogHeader><DialogTitle>Practice journal · {selected?.worker_name || "Unnamed learner"}</DialogTitle><DialogDescription>Read-only, unverified phone report. Not an assessment score, safety certification or authorisation to work.</DialogDescription></DialogHeader>
      {snapshot && <>
        <div className="rounded-xl bg-slate-50 p-4"><strong>{snapshot.module === "fire" ? "Fire" : "Gas"} · {snapshot.mode === "camera" ? "Camera reported" : "Screen practice"} · engine v{snapshot.mission.version}</strong><p>{snapshot.mission.completed ? "Virtual journey complete" : "Incomplete"} · Current phase: {readable(snapshot.mission.phase)}</p><p>Reported outcome: {readable(snapshot.mission.result.outcome)}.</p><p className="mt-2 text-sm">Practical observation: not assessed. {snapshot.mission.version === 1 ? "Legacy mission: a shorter journey; do not equate it with current module coverage." : "Game timing and target tolerances are interaction settings, not equipment operating standards."}</p></div>
        {historyError && <p role="alert">{historyError}</p>}
        {history.length > 0 && <label className="text-sm">Saved snapshot<select className="mt-1 block w-full rounded-lg border p-3" value={history.find(h => h.payload.updatedAt === snapshot.updatedAt)?.digest ?? ""} onChange={e => { const item = history.find(h => h.digest === e.target.value);if (item) setSnapshot(item.payload); }}>{history.map(h => <option key={h.digest} value={h.digest}>{new Date(h.captured_at).toLocaleString()} · {h.payload.mission.completed ? "complete journey" : readable(h.payload.mission.phase)}</option>)}</select>{historyTotal > history.length && <span>Latest {history.length} of {historyTotal} immutable snapshots shown.</span>}</label>}
        <h3 className="font-semibold">Recorded actions</h3><p className="text-sm text-slate-600">Times are phone elapsed milliseconds, not wall-clock timestamps. Consistency checks replay reported sequence; they cannot attest that the learner performed physical actions.</p>
        {snapshot.mission.events.length ? <ol className="space-y-2">{snapshot.mission.events.map((e, i) => <li key={i} className="rounded-lg border p-3 text-sm"><strong>{i + 1}. {readable(e.id)}</strong> · {e.accepted ? "accepted by simulation" : "not accepted"}<div className="text-slate-600">{readable(e.phase)} · {e.at} ms · {readable(e.reason)}</div></li>)}</ol> : <p>No intentional actions saved yet.</p>}
        <h3 className="font-semibold">Help requested</h3><p>{snapshot.coaching?.mode === "recall" ? "Recall practice" : "Guided practice"} · {snapshot.coaching?.cues.length ?? 0} requested hints.</p>
        {(snapshot.coaching?.cues.length ?? 0) > 0 && <ul>{snapshot.coaching!.cues.map((cue, i) => <li key={i}>{readable(cue.phase)} · {cue.at} elapsed ms</li>)}</ul>}
        <h3 className="font-semibold">Virtual interaction measurements</h3>{snapshot.mission.measurements.length ? <ul>{snapshot.mission.measurements.map(value => <li key={value.kind}>{readable(value.kind)} · {value.durationMs} ms · {value.samples} samples · maximum reported gap {value.maxGapMs} ms</li>)}</ul> : <p>No completed timed interaction recorded.</p>}
        {snapshot.mission.lastInterruption && <p>Last partial-action interruption: {readable(snapshot.mission.lastInterruption)}.</p>}
      </>}
    </DialogContent></Dialog>
  </section>;
}
