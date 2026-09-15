import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { assertRoomSuccessor, validateRoomImport, validateRoomJournal } from "../lib/room-journals";
import { roomExport, roomFixture, worker } from "./room-fixtures";

test("legacy and v2 fire/gas journals stay noncertifying, including incomplete and evacuation-only", () => {
  for (const version of [1, 2] as const) for (const module of ["fire", "gas"] as const) for (const complete of [false, true]) {
    const record = roomFixture(version, module, complete);
    assert.equal(validateRoomImport(roomExport([record])).rooms[0].mission.result.certifiable, false);
    record.mode = "screen";delete record.coaching;assert.equal(validateRoomJournal(record, worker.id).mode, "screen");
  }
  const evacuation = roomFixture(2, "fire", true, true);evacuation.mission.scenario.explosionRisk = true;evacuation.mission.scenario.explosionRiskSource = "scenario-announced";
  assert.equal(validateRoomJournal(evacuation, worker.id).mission.result.outcome, "assembled-reported-without-discharge");
});
test("reject owner, version, forged certification, unknown fields and missing or reordered actions", () => {
  const mutations = [
    (r: any) => r.workerId = "33333333-3333-4333-8333-333333333333", (r: any) => r.module = "gas",
    (r: any) => r.mode = "mixed", (r: any) => r.mission.version = 3, (r: any) => r.mission.result.certifiable = true,
    (r: any) => r.mission.result.practical = "assessed", (r: any) => r.mission.result.score = 100,
    (r: any) => r.mission.events.shift(), (r: any) => r.mission.events.reverse(),
    (r: any) => r.mission.events[0].id = "invented", (r: any) => r.mission.events[0].accepted = false,
    (r: any) => r.mission.events = r.mission.events.filter((e: any) => e.id !== "release"),
    (r: any) => r.mission.overallProgress = .5, (r: any) => r.mission.scenario.realAuthorisationGranted = true,
    (r: any) => r.mission.scenario.explosionRisk = true, (r: any) => r.updatedAt = Infinity,
  ];
  for (const mutate of mutations) { const record = roomFixture();mutate(record);assert.throws(() => validateRoomImport(roomExport([record])), /Invalid room journal/); }
});
test("virtual measurements bind to events and cannot fabricate timing or sweep coverage", () => {
  for (const mutate of [(r: any) => r.mission.measurements[0].durationMs = 299, (r: any) => r.mission.measurements[1].bins = [0, 4],
    (r: any) => r.mission.measurements[1].maxGapMs = 151, (r: any) => r.mission.measurements[1].samples = 2,
    (r: any) => r.mission.measurements[0].completedAt++, (r: any) => r.mission.measurements.push(r.mission.measurements[0])]) {
    const record = roomFixture();mutate(record);assert.throws(() => validateRoomJournal(record, worker.id));
  }
});
test("requested cues stay ordered, phase-bound and separate from guided practice", () => {
  const record = roomFixture();record.coaching = { version: 1, mode: "recall", cues: [{ phase: "ALARM", at: 50 }, { phase: "EXIT", at: 150 }] };
  validateRoomJournal(record, worker.id);
  for (const cues of [[{ phase: "ALARM", at: 500 }], [{ phase: "COMPLETE", at: 10000 }], [{ phase: "EXIT", at: 150 }, { phase: "ALARM", at: 50 }]]) {
    assert.throws(() => validateRoomJournal({ ...record, coaching: { ...record.coaching, cues } }, worker.id));
  }
  assert.throws(() => validateRoomJournal({ ...record, coaching: { ...record.coaching, mode: "guided" } }, worker.id));
});
test("bounded envelopes reject oversized history, duplicate IDs and unknown schema", () => {
  const record = roomFixture();
  for (const batch of [{ ...roomExport([record]), schemaVersion: 2 }, roomExport([]), roomExport([record, record]), roomExport(Array(101).fill(record))]) assert.throws(() => validateRoomImport(batch));
  record.mission.events = Array(513).fill(record.mission.events[0]);assert.throws(() => validateRoomImport(roomExport([record])));
});
test("new completion appends to incomplete snapshot, while changed identity or prior history conflicts", () => {
  const previous = roomFixture(2, "fire", false), next = roomFixture();
  assertRoomSuccessor(validateRoomJournal(previous, worker.id), validateRoomJournal(next, worker.id));
  for (const mutate of [(r: any) => r.mode = "screen", (r: any) => r.updatedAt = previous.updatedAt,
    (r: any) => r.mission.events[0].at++, (r: any) => r.mission.events.shift(), (r: any) => r.coaching.mode = "recall",
    (r: any) => r.mission.scenario.explosionRisk = true]) { const changed = structuredClone(next);mutate(changed);assert.throws(() => assertRoomSuccessor(previous, changed), /Record conflict/); }
  const recalledBefore = structuredClone(previous), recalledNext = structuredClone(next);
  recalledBefore.coaching = { version: 1, mode: "recall", cues: [{ phase: "ALARM", at: 50 }] };
  recalledNext.coaching = { version: 1, mode: "recall", cues: [{ phase: "ALARM", at: 50 }, { phase: "EQUIPMENT", at: 250 }] };
  assertRoomSuccessor(recalledBefore, recalledNext);recalledNext.coaching.cues.shift();assert.throws(() => assertRoomSuccessor(recalledBefore, recalledNext));
});
test("SQLite guard atomically rejects stale-parent forks without losing the previously imported snapshot", () => {
  const db = new DatabaseSync(":memory:");db.exec(readFileSync(new URL("../drizzle/0002_room_journals.sql", import.meta.url), "utf8"));
  const insert = db.prepare("INSERT INTO room_journal_snapshots VALUES(?,?,?,?,?,?,?)");
  const head = db.prepare("INSERT INTO room_journal_heads VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(owner,id) DO UPDATE SET digest=excluded.digest");
  const append = (hash: string, parent: string | null) => { db.exec("BEGIN");try { insert.run("trainer", "attempt", hash, parent, 1, 1, "{}");head.run("trainer", "attempt", "worker", "Learner", "Mining", hash, 1, "{}", 1);db.exec("COMMIT"); } catch (e) { db.exec("ROLLBACK");throw e; } };
  append("one", null);append("two", "one");assert.throws(() => append("fork", "one"), /changed concurrently/);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM room_journal_snapshots").get() as {n:number}).n, 2);
  assert.equal((db.prepare("SELECT digest FROM room_journal_heads").get() as {digest:string}).digest, "two");db.close();
});
