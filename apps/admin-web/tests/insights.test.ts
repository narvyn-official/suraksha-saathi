import { test } from "node:test";
import assert from "node:assert/strict";
import { insights, workerCsv, type TrainingRow } from "../lib/insights";
import { curriculum, curriculumFor, grade } from "../lib/grading";
function row(
  id: string,
  moduleId = "fire",
  passed = true,
  endedAt = 100,
  kind = "assessment",
): TrainingRow {
  const m = curriculum.modules.find((m) => m.id === moduleId)!;
  return {
    id,
    worker_id: "worker-1",
    worker_name: "Example",
    worker_sector: "Mining",
    payload: {
      moduleId,
      kind,
      endedAt,
      contentVersion: curriculum.version,
      result: { passed, score: passed ? 100 : 0 },
      events: m.questions.map((q, i) => ({
        questionId: q.id,
        optionId: q.options.find((o) => (passed ? o.correct : !o.correct))!.id,
        sequence: i + 1,
        type: "answer",
        time: endedAt,
      })),
    },
  };
}
test("latest failure replaces earlier pass; practice cannot inflate coverage", () => {
  const data = insights(
    [
      row("old"),
      row("new", "fire", false, 200),
      row("practice", "gas", true, 300, "practice"),
    ],
    [],
  );
  assert.equal(data.workers.length, 1);
  assert.equal(data.workers[0].passed, 0);
  assert.equal(data.needsPractice, 1);
  assert.equal(data.modules[1].notAssessed, 1);
  assert.ok(data.hotspots[0].critical);
  assert.equal(data.hotspots[0].answered, 1);
});
test("all available latest assessments required and revoked credentials excluded", () => {
  const rows = curriculum.modules.map((m) => row(m.id, m.id));
  const data = insights(rows, [
    { id: "a", attempt_id: "fire", revoked_at: 1 },
    { id: "b", attempt_id: "gas", revoked_at: null },
  ]);
  assert.equal(data.fullyPassed, 1);
  assert.equal(data.workers[0].activeCredentials, 1);
  assert.equal(data.workers[0].status, "All simulations passed");
});
test("CSV preserves quoting and neutralizes spreadsheet formulas", () => {
  const r = row("one");
  r.worker_name = '=SUM(1,2) "test"';
  const csv = workerCsv(insights([r], []).workers);
  assert.ok(csv.includes('"\'=SUM(1,2) ""test"""'));
});
test("machinery critical failure overrides a high numerical score", () => {
  const r = row("one", "machinery");
  const q = curriculum.modules[2].questions[0];
  r.payload.events[0].optionId = q.options.find((o) => !o.correct)!.id;
  const result = grade("machinery", r.payload.events);
  assert.equal(result.score, 87);
  assert.equal(result.passed, false);
  assert.equal(result.criticalFailures.length, 1);
});
test("archived content accepts its modules but rejects a new module or unknown version", () => {
  assert.equal(grade("fire", row("one").payload.events, "0.1.0").passed, true);
  assert.throws(() => grade("machinery", [], "0.1.0"));
  assert.throws(() => grade("fire", [], "99.0.0"));
});

test("PPE has its own analytics label, critical gate and version scope", () => {
  const correct = row("ppe", "ppe");
  assert.equal(grade("ppe", correct.payload.events).passed, true);
  assert.throws(() => grade("ppe", correct.payload.events, "0.2.0"));
  assert.equal(
    grade("machinery", row("old", "machinery").payload.events, "0.2.0").passed,
    true,
  );
  const q = curriculum.modules.find((m) => m.id === "ppe")!.questions[0];
  correct.payload.events[0].optionId = q.options.find((o) => !o.correct)!.id;
  assert.equal(grade("ppe", correct.payload.events).passed, false);
  const data = insights([row("ppe", "ppe")], []);
  assert.equal(data.modules.find((m) => m.id === "ppe")?.name, "PPE");
  assert.equal(data.fullyPassed, 0);
});

test("all five current domains are required for complete coverage", () => {
  assert.deepEqual(
    curriculum.modules.map((m) => m.id),
    ["fire", "gas", "machinery", "ppe", "emergency"],
  );
  const firstFour = curriculum.modules
    .filter((m) => m.id !== "emergency")
    .map((m) => row(m.id, m.id));
  const incomplete = insights(firstFour, []);
  assert.equal(incomplete.fullyPassed, 0);
  assert.equal(incomplete.workers[0].status, "In progress");
  assert.equal(
    incomplete.modules.find((m) => m.id === "emergency")?.notAssessed,
    1,
  );
  const completed = insights([...firstFour, row("emergency", "emergency")], []);
  assert.equal(completed.fullyPassed, 1);
  assert.equal(completed.workers[0].passed, 5);
  assert.equal(
    completed.modules.find((m) => m.id === "emergency")?.name,
    "Emergency",
  );
});

test("an emergency critical error cannot be averaged into a pass", () => {
  const r = row("emergency", "emergency");
  assert.equal(grade("emergency", r.payload.events).passed, true);
  const module = curriculum.modules.find((m) => m.id === "emergency")!;
  const index = module.questions.findIndex((q) => q.critical);
  assert.ok(index >= 0);
  r.payload.events[index].optionId = module.questions[index].options.find(
    (o) => !o.correct,
  )!.id;
  const result = grade("emergency", r.payload.events);
  assert.ok(result.score >= 80);
  assert.equal(result.passed, false);
  assert.deepEqual(result.criticalFailures, [module.questions[index].id]);
});

test("0.3.0 replays its four archived domains and cannot claim emergency evidence", () => {
  const archived = curriculumFor("0.3.0");
  assert.deepEqual(
    archived.modules.map((m) => m.id),
    ["fire", "gas", "machinery", "ppe"],
  );
  for (const m of archived.modules) {
    const events = m.questions.map((q, i) => ({
      type: "answer",
      sequence: i + 1,
      questionId: q.id,
      optionId: q.options.find((o) => o.correct)!.id,
      time: i + 1,
    }));
    assert.equal(grade(m.id, events, "0.3.0").passed, true);
  }
  assert.throws(
    () => grade("emergency", row("new", "emergency").payload.events, "0.3.0"),
    /Unknown module/,
  );
});
