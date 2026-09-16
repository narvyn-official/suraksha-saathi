import { z } from "zod/v3";

const time = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const id = z.string().uuid();
const event = z.object({ id: z.string().min(1).max(64), phase: z.string().max(32), at: time,
  accepted: z.boolean(), reason: z.enum(["intentional-control", "unsafe-control", "release-required", "out-of-order", "virtual-measurement"]) }).strict();
const measurement = z.object({ kind: z.enum(["base-alignment", "base-sweep"]), startedAt: time, completedAt: time,
  durationMs: time, samples: z.number().int().min(2).max(10_000_000), maxGapMs: z.number().int().min(1).max(150),
  bins: z.array(z.number().int().min(0).max(4)).max(5) }).strict();
const scenario = z.object({ simulated: z.literal(true), catalogVersion: z.literal(1),
  meter: z.literal("SIM").optional(), liveReading: z.literal(false).optional(), rescueReadiness: z.literal("unconfirmed").optional(), entryAuthorised: z.literal(false).optional(),
  ppe: z.literal("draft-scenario-kit-for-outside-role").optional(), ppeAuthorisesEntry: z.literal(false).optional(), buddyCommunication: z.enum(["unconfirmed", "scenario-acknowledged"]).optional(),
  role: z.literal("scenario-authorised").optional(), realAuthorisationGranted: z.literal(false).optional(),
  explosionRisk: z.boolean().optional(), explosionRiskSource: z.literal("scenario-announced").optional(),
  equipment: z.enum(["scenario-suitable", "not-used", "scenario-suitable-selected", "selection-pending"]).optional(),
  retreatPath: z.enum(["scenario-clear", "scenario-clear-selected", "selection-pending"]).optional(),
  missingPerson: z.literal("scenario-colleague-unaccounted-for").optional(), reentryAuthorised: z.literal(false).optional() }).strict();
const mission = z.object({ version: z.union([z.literal(1), z.literal(2)]), module: z.enum(["fire", "gas"]), phase: z.string().max(32),
  completed: z.boolean(), progress: z.number().min(0).max(1), overallProgress: z.number().min(0).max(1), released: z.boolean(), evacuationOnly: z.boolean().optional(),
  lastInterruption: z.enum(["stale-frame", "frame-gap", "off-base", "wrong-start-edge", "skipped-band", "released", "clock-invalid"]).nullable().optional(),
  events: z.array(event).max(512), measurements: z.array(measurement).max(2), scenario,
  result: z.object({ complete: z.boolean(), certifiable: z.literal(false), practical: z.literal("not-assessed"),
    outcome: z.enum(["in-progress", "outside-entry-refused", "withdrawn-after-worsening", "assembled-reported-without-discharge", "assembled-reported-after-worsening"]) }).strict() }).strict();
const coaching = z.object({ version: z.literal(1), mode: z.enum(["guided", "recall"]), cues: z.array(z.object({ phase: z.string().max(32), at: time }).strict()).max(128) }).strict();
const journal = z.object({ id, workerId: id, module: z.enum(["fire", "gas"]), mode: z.enum(["camera", "screen"]), updatedAt: time.max(8_640_000_000_000_000), mission, coaching: coaching.optional() }).strict();
const envelope = z.object({ schemaVersion: z.literal(1), kind: z.literal("room-mission-journal"), exportedAt: time.max(8_640_000_000_000_000),
  omittedCount: z.number().int().min(0).max(1_000_000).optional(),
  worker: z.object({ id, name: z.string().max(80), sector: z.enum(["Unspecified", "Mining", "Steel", "Mica", "Other"]) }).strict(),
  rooms: z.array(journal).min(1).max(100) }).strict();
export type RoomJournal = z.infer<typeof journal>;
export type RoomJournalImport = z.infer<typeof envelope>;
export type RoomJournalRow = { id: string; worker_id: string; worker_name: string; worker_sector: string;
  digest: string; captured_at: number; imported_at: number; snapshots: number; payload: RoomJournal };

const controlsV1 = ["alarm", "pin-drag", "release", "withdraw", "inspect-meter", "close-barrier", "place-attendant-outside", "refuse-entry"];
const unsafeV1 = ["enter", "place-attendant-inside", "continue-discharge"];
const controlsV2 = [...controlsV1, "select-clear-exit", "select-suitable-extinguisher", "choose-evacuation", "follow-clear-route", "reach-assembly-point", "report-missing-worker", "select-specified-ppe", "send-buddy-check", "confirm-buddy-ack"];
const unsafeV2 = [...unsafeV1, "select-blocked-exit", "select-unsuitable-extinguisher", "operate-without-training", "follow-blocked-route", "enter-smoke", "leave-without-rollcall", "reenter-search", "select-dust-mask", "skip-buddy-check", "proceed-without-ack"];
export function roomPhases(module: "fire" | "gas", version: number) {
  if (version === 1) return module === "fire" ? ["ALARM", "PIN", "AIM", "SWEEP", "WITHDRAW", "COMPLETE"] : ["GAS_CHECK", "BARRIER", "ATTENDANT", "REFUSE", "COMPLETE"];
  return module === "fire" ? ["ALARM", "EXIT", "EQUIPMENT", "PIN", "AIM", "SWEEP", "WITHDRAW", "EVACUATE", "ASSEMBLY", "REPORT", "COMPLETE"] : ["GAS_CHECK", "PPE", "BARRIER", "ATTENDANT", "COMMUNICATE", "ACKNOWLEDGE", "REFUSE", "COMPLETE"];
}
function requireValid(valid: unknown, message: string): asserts valid { if (!valid) throw new Error(`Invalid room journal: ${message}.`); }
/** Consistency replay, not attestation of camera tracking, worker identity or physical skill. */
export function validateRoomJournal(input: unknown, workerId: string): RoomJournal {
  const parsed = journal.safeParse(input);
  requireValid(parsed.success, "malformed or unsupported snapshot");
  const r = parsed.data, m = r.mission, phases = roomPhases(r.module, m.version);
  requireValid(r.workerId === workerId && m.module === r.module, "owner or module mismatch");
  const unsafe = m.version === 1 ? unsafeV1 : unsafeV2, controls = m.version === 1 ? controlsV1 : controlsV2;
  const expected: Record<string, string> = { alarm: "ALARM", "pin-drag": "PIN", "select-clear-exit": "EXIT", "select-suitable-extinguisher": "EQUIPMENT", "choose-evacuation": "EQUIPMENT", "follow-clear-route": "EVACUATE", "reach-assembly-point": "ASSEMBLY", "report-missing-worker": "REPORT", "inspect-meter": "GAS_CHECK", "select-specified-ppe": "PPE", "close-barrier": "BARRIER", "place-attendant-outside": "ATTENDANT", "send-buddy-check": "COMMUNICATE", "confirm-buddy-ack": "ACKNOWLEDGE", "refuse-entry": "REFUSE" };
  let index = 0, released = false, evacuation = false, lastAt = -1, measured = 0;
  const intervals: { phase: string; from: number; to: number }[] = [];
  let entered = 0;
  for (const e of m.events) {
    const phase = phases[index];
    requireValid(phase !== "COMPLETE" && e.phase === phase && e.at >= lastAt, "event sequence or time");
    lastAt = e.at;
    const virtual = e.id === "base-alignment" || e.id === "base-sweep";
    const risk = e.id === "select-suitable-extinguisher" && phase === "EQUIPMENT" && m.scenario.explosionRisk === true;
    let accepted = false, reason: string;
    if (virtual) {
      requireValid(r.module === "fire" && phase === (e.id === "base-alignment" ? "AIM" : "SWEEP"), "measurement phase");
      const v = m.measurements[measured++];
      requireValid(v && v.kind === e.id && v.startedAt >= entered && v.completedAt === e.at && v.durationMs === v.completedAt - v.startedAt, "measurement binding");
      requireValid(v.durationMs >= (e.id === "base-alignment" ? 300 : 1500) && v.durationMs <= (v.samples - 1) * v.maxGapMs, "measurement timing");
      requireValid(e.id === "base-alignment" ? v.bins.length === 0 : ["0,1,2,3,4", "4,3,2,1,0"].includes(v.bins.join(",")), "measurement coverage");
      accepted = true; reason = "virtual-measurement";
    } else {
      requireValid(controls.includes(e.id) || unsafe.includes(e.id), "unknown control");
      accepted = e.id === "release" ? phase === "SWEEP" || (phase === "WITHDRAW" && !released)
        : e.id === "withdraw" ? phase === "WITHDRAW" && released : expected[e.id] === phase && !risk;
      reason = accepted ? "intentional-control" : unsafe.includes(e.id) || risk ? "unsafe-control"
        : e.id === "withdraw" && phase === "WITHDRAW" ? "release-required" : "out-of-order";
    }
    requireValid(e.accepted === accepted && e.reason === reason, "control acceptance");
    if (!accepted) continue;
    if (e.id === "release") { if (phase === "WITHDRAW") released = true; continue; }
    intervals.push({ phase, from: entered, to: e.at });entered = e.at;
    if (e.id === "choose-evacuation") { evacuation = true; index = phases.indexOf("EVACUATE"); } else index++;
  }
  intervals.push({ phase: phases[index], from: entered, to: Number.MAX_SAFE_INTEGER });
  requireValid(measured === m.measurements.length && m.phase === phases[index] && m.released === released, "snapshot state does not match its journal");
  requireValid(m.version === 1 ? m.evacuationOnly === undefined : m.evacuationOnly === evacuation, "evacuation branch");
  const completed = m.phase === "COMPLETE";
  requireValid(m.completed === completed && m.result.complete === completed, "completion status");
  const outcome = !completed ? "in-progress" : r.module === "gas" ? "outside-entry-refused" : m.version === 1 ? "withdrawn-after-worsening" : evacuation ? "assembled-reported-without-discharge" : "assembled-reported-after-worsening";
  requireValid(m.result.outcome === outcome, "outcome");
  const fixedProgress = completed ? 1 : m.phase === "WITHDRAW" && released ? .5 : 0;
  requireValid(["AIM", "SWEEP"].includes(m.phase) || m.progress === fixedProgress, "phase progress");
  requireValid(Math.abs(m.overallProgress - (completed ? 1 : (index + m.progress) / (phases.length - 1))) <= 0.000001, "overall progress");
  const s = m.scenario;
  if (r.module === "gas") {
    requireValid(s.meter === "SIM" && s.liveReading === false && s.rescueReadiness === "unconfirmed" && s.entryAuthorised === false, "gas simulation boundaries");
    requireValid(s.role === undefined && s.explosionRisk === undefined && s.equipment === undefined && s.retreatPath === undefined && s.realAuthorisationGranted === undefined && s.explosionRiskSource === undefined && s.missingPerson === undefined && s.reentryAuthorised === undefined, "fire fields in gas snapshot");
    if (m.version === 2) requireValid(s.ppe === "draft-scenario-kit-for-outside-role" && s.ppeAuthorisesEntry === false && s.buddyCommunication === (m.events.some(e => e.id === "confirm-buddy-ack" && e.accepted) ? "scenario-acknowledged" : "unconfirmed"), "outside-role PPE or communication");
  } else {
    requireValid(s.role === "scenario-authorised" && s.meter === undefined && s.liveReading === undefined && s.rescueReadiness === undefined && s.entryAuthorised === undefined && s.ppe === undefined && s.ppeAuthorisesEntry === undefined && s.buddyCommunication === undefined, "fire simulation boundaries");
    if (m.version === 1) requireValid(s.equipment === "scenario-suitable" && s.retreatPath === "scenario-clear" && s.explosionRisk === undefined && s.explosionRiskSource === undefined, "legacy fire scenario");
    else {
      requireValid(s.realAuthorisationGranted === false && s.reentryAuthorised === false && s.missingPerson === "scenario-colleague-unaccounted-for", "fire authorisation boundaries");
      requireValid(s.equipment === (evacuation ? "not-used" : m.events.some(e => e.id === "select-suitable-extinguisher" && e.accepted) ? "scenario-suitable-selected" : "selection-pending"), "equipment selection");
      requireValid(s.retreatPath === (m.events.some(e => e.id === "select-clear-exit" && e.accepted) ? "scenario-clear-selected" : "selection-pending"), "exit selection");
      requireValid(s.explosionRisk ? s.explosionRiskSource === "scenario-announced" : s.explosionRiskSource === undefined, "explosion scenario attribution");
    }
  }
  const cues = r.coaching?.cues ?? [];
  requireValid(r.coaching?.mode !== "guided" || cues.length === 0, "guided cue history");
  let lastCue = -1;
  for (const cue of cues) {
    requireValid(cue.at >= lastCue && cue.phase !== "COMPLETE" && intervals.some(i => i.phase === cue.phase && cue.at >= i.from && cue.at <= i.to), "cue phase or time");lastCue = cue.at;
  }
  return r;
}
export function validateRoomImport(input: unknown): RoomJournalImport {
  const parsed = envelope.safeParse(input);requireValid(parsed.success, "expected a bounded room-mission-journal export");
  const data = parsed.data, ids = new Set<string>();
  for (const room of data.rooms) { requireValid(!ids.has(room.id), "duplicate attempt ID in one export");ids.add(room.id);validateRoomJournal(room, data.worker.id); }
  return data;
}
function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object") return `{${Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => `${JSON.stringify(k)}:${canonical(v)}`).join(",")}}`;
  return JSON.stringify(value);
}
export function assertRoomSuccessor(previous: RoomJournal, next: RoomJournal) {
  const sameIdentity = previous.id === next.id && previous.workerId === next.workerId && previous.module === next.module && previous.mode === next.mode && previous.mission.version === next.mission.version && (previous.coaching?.mode ?? "guided") === (next.coaching?.mode ?? "guided") && (previous.mission.scenario.explosionRisk ?? false) === (next.mission.scenario.explosionRisk ?? false);
  if (!sameIdentity || next.updatedAt <= previous.updatedAt) throw new Error("Record conflict: room identity or snapshot time changed.");
  const prefix = (a: unknown[], b: unknown[]) => a.length <= b.length && a.every((item, i) => canonical(item) === canonical(b[i]));
  if (!prefix(previous.mission.events, next.mission.events) || !prefix(previous.mission.measurements, next.mission.measurements) || !prefix(previous.coaching?.cues ?? [], next.coaching?.cues ?? [])) throw new Error("Record conflict: previous room history cannot be changed or removed.");
  // New cues cannot be backfilled into a previously captured phase interval.
  const previousTime = Math.max(0, ...previous.mission.events.map(e => e.at), ...(previous.coaching?.cues ?? []).map(c => c.at));
  if ((next.coaching?.cues ?? []).slice(previous.coaching?.cues.length ?? 0).some(c => c.at < previousTime)) throw new Error("Record conflict: room help cannot be added before saved actions.");
}

/** Bound decoded input by bytes while reading, including direct API callers and multibyte JSON. */
export async function readRoomImport(request: Request): Promise<RoomJournalImport> {
  const origin = request.headers.get("origin");
  if (origin && origin !== new URL(request.url).origin) throw new Error("Invalid request origin.");
  const reader = request.body?.getReader();
  if (!reader) throw new Error("Invalid room journal JSON.");
  const chunks: Uint8Array[] = [];let length = 0;
  try {
    while (true) { const { value, done } = await reader.read();if (done) break;length += value.byteLength;
      if (length > 1_000_000) { await reader.cancel();throw new Error("File is too large. Maximum 1 MB."); }chunks.push(value); }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(length);let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset);offset += chunk.byteLength; }
  let data: unknown;
  try { data = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
  catch { throw new Error("Invalid room journal JSON."); }
  return validateRoomImport(data);
}
