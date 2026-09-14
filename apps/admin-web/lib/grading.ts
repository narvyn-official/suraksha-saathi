import curriculum from "./curriculum.json" with { type: "json" };
export { curriculum };
export function grade(moduleId: string, events: unknown) {
  const module = curriculum.modules.find((m) => m.id === moduleId);
  if (!module) throw new Error("Unknown module.");
  if (!Array.isArray(events) || events.length > module.questions.length)
    throw new Error("Invalid answer sequence.");
  let correct = 0;
  const criticalFailures: string[] = [];
  for (let i = 0; i < events.length; i++) {
    const e = events[i],
      q = module.questions[i];
    if (
      !e ||
      e.type !== "answer" ||
      e.sequence !== i + 1 ||
      e.questionId !== q.id ||
      !Number.isSafeInteger(e.time)
    )
      throw new Error("Invalid answer sequence.");
    const option = q.options.find((o) => o.id === e.optionId);
    if (!option) throw new Error("Invalid answer option.");
    if (option.correct) correct++;
    else if (q.critical) criticalFailures.push(q.id);
  }
  const complete = events.length === module.questions.length,
    score = Math.floor((correct * 100) / module.questions.length);
  return {
    score,
    correct,
    total: module.questions.length,
    complete,
    valid: true,
    criticalFailures,
    passed: complete && score >= 80 && !criticalFailures.length,
  };
}
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export function validateImport(input: any) {
  if (
    input?.schemaVersion !== 1 ||
    !uuid.test(input?.worker?.id) ||
    typeof input.worker.name !== "string" ||
    input.worker.name.length > 80 ||
    !Array.isArray(input.attempts) ||
    !input.attempts.length ||
    input.attempts.length > 100
  )
    throw new Error(
      "Invalid training export. Expected 1–100 completed attempts.",
    );
  const ids = new Set();
  const attempts = input.attempts.map((a: any) => {
    if (
      !a ||
      !uuid.test(a.id) ||
      ids.has(a.id) ||
      a.workerId !== input.worker.id ||
      a.contentVersion !== curriculum.version ||
      !["practice", "assessment"].includes(a.kind) ||
      !["screen", "arcore", "hybrid"].includes(a.mode) ||
      a.finished !== true ||
      !Number.isSafeInteger(a.startedAt) ||
      !Number.isSafeInteger(a.endedAt) ||
      a.endedAt < a.startedAt ||
      a.startedAt < 0
    )
      throw new Error("Invalid or unsupported training attempt.");
    ids.add(a.id);
    const result = grade(a.moduleId, a.events);
    if (
      a.events.some(
        (e: any, i: number) =>
          e.time < a.startedAt ||
          e.time > a.endedAt ||
          (i && e.time < a.events[i - 1].time),
      )
    )
      throw new Error("Invalid event timestamps.");
    if (
      !result.complete &&
      !(a.kind === "assessment" && result.criticalFailures.length === 1)
    )
      throw new Error("Invalid incomplete attempt.");
    if (a.kind === "assessment" && result.criticalFailures.length) {
      const first = a.events.findIndex((e: any) =>
        result.criticalFailures.includes(e.questionId),
      );
      if (first !== a.events.length - 1)
        throw new Error("Invalid answers after critical failure.");
    }
    return { ...a, result };
  });
  return { worker: input.worker, attempts };
}
