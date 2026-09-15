import type { RoomJournal } from "../lib/room-journals";
import { roomPhases } from "../lib/room-journals";
export const worker = { id: "11111111-1111-4111-8111-111111111111", name: "Room journal test learner", sector: "Mining" as const };
export function roomFixture(version: 1 | 2 = 2, module: "fire" | "gas" = "fire", complete = true, evacuation = false): RoomJournal {
  const phases = roomPhases(module, version);
  const controls = module === "gas" ? (version === 1 ? ["inspect-meter", "close-barrier", "place-attendant-outside", "refuse-entry"] : ["inspect-meter", "select-specified-ppe", "close-barrier", "place-attendant-outside", "send-buddy-check", "confirm-buddy-ack", "refuse-entry"])
    : version === 1 ? ["alarm", "pin-drag", "base-alignment", "base-sweep", "release", "withdraw"]
    : ["alarm", "select-clear-exit", ...(evacuation ? ["choose-evacuation"] : ["select-suitable-extinguisher", "pin-drag", "base-alignment", "base-sweep", "release", "withdraw"]), "follow-clear-route", "reach-assembly-point", "report-missing-worker"];
  let index = 0, at = 0, released = false, evacuated = false;
  const events: RoomJournal["mission"]["events"] = [], measurements: RoomJournal["mission"]["measurements"] = [];
  for (const id of complete ? controls : controls.slice(0, 2)) {
    const phase = phases[index];at += 100;
    const virtual = id === "base-alignment" || id === "base-sweep";
    if (virtual) { const duration = id === "base-alignment" ? 300 : 1500;measurements.push({ kind: id, startedAt: at, completedAt: at + duration, durationMs: duration, samples: duration / 100 + 1, maxGapMs: 100, bins: id === "base-sweep" ? [0, 1, 2, 3, 4] : [] });at += duration; }
    events.push({ id, phase, at, accepted: true, reason: virtual ? "virtual-measurement" : "intentional-control" });
    if (id === "release") released = true;
    else if (id === "choose-evacuation") { evacuated = true;index = phases.indexOf("EVACUATE"); }
    else index++;
  }
  const selected = (id: string) => events.some(e => e.id === id);
  const scenario: RoomJournal["mission"]["scenario"] = module === "gas" ? { simulated: true, catalogVersion: 1, meter: "SIM", liveReading: false, rescueReadiness: "unconfirmed", entryAuthorised: false,
    ...(version === 2 ? { ppe: "draft-scenario-kit-for-outside-role" as const, ppeAuthorisesEntry: false as const, buddyCommunication: selected("confirm-buddy-ack") ? "scenario-acknowledged" as const : "unconfirmed" as const } : {}) }
    : version === 1 ? { simulated: true, catalogVersion: 1, role: "scenario-authorised", equipment: "scenario-suitable", retreatPath: "scenario-clear" }
    : { simulated: true, catalogVersion: 1, role: "scenario-authorised", realAuthorisationGranted: false, explosionRisk: false, reentryAuthorised: false, missingPerson: "scenario-colleague-unaccounted-for", equipment: evacuated ? "not-used" : selected("select-suitable-extinguisher") ? "scenario-suitable-selected" : "selection-pending", retreatPath: selected("select-clear-exit") ? "scenario-clear-selected" : "selection-pending" };
  return { id: "22222222-2222-4222-8222-222222222222", workerId: worker.id, module, mode: "camera", updatedAt: complete ? 2000 : 1000,
    mission: { version, module, phase: phases[index], completed: complete, progress: complete ? 1 : 0, overallProgress: complete ? 1 : index / (phases.length - 1), released, ...(version === 2 ? { evacuationOnly: evacuated } : {}), lastInterruption: null, events, measurements, scenario,
      result: { complete, certifiable: false, practical: "not-assessed", outcome: !complete ? "in-progress" : module === "gas" ? "outside-entry-refused" : version === 1 ? "withdrawn-after-worsening" : evacuated ? "assembled-reported-without-discharge" : "assembled-reported-after-worsening" } },
    coaching: { version: 1, mode: "guided", cues: [] } };
}
export function roomExport(rooms: RoomJournal[]) { return { schemaVersion: 1, kind: "room-mission-journal", exportedAt: 3000, omittedCount: 0, worker, rooms }; }
