import { z } from "zod";
import curriculum from "./curriculum.json" with { type: "json" };
import legacy from "./archive/0.1.0.json" with { type: "json" };
import v02 from "./archive/0.2.0.json" with { type: "json" };
import v03 from "./archive/0.3.0.json" with { type: "json" };

const eventSchema = z.object({ type:z.literal("answer"), sequence:z.number().int().safe(), questionId:z.string(), optionId:z.string(), time:z.number().int().safe() }).passthrough();
export type AnswerEvent = z.infer<typeof eventSchema>;
const attemptSchema = z.object({ id:z.string(),workerId:z.string(),moduleId:z.string(),contentVersion:z.string(),kind:z.enum(["practice","assessment"]),mode:z.enum(["screen","arcore","hybrid"]),finished:z.literal(true),startedAt:z.number().int().safe(),endedAt:z.number().int().safe(),events:z.array(eventSchema) }).passthrough();
const exportSchema = z.object({schemaVersion:z.literal(1),worker:z.object({id:z.string(),name:z.string(),sector:z.string().optional()}).passthrough(),attempts:z.array(attemptSchema).min(1).max(100)});

const supported = [curriculum, v03, v02, legacy];
export { curriculum };
export function curriculumFor(version: string) {
  const found = supported.find((c) => c.version === version);
  if (found) return found;
  throw new Error("Unsupported content version.");
}
export function grade(
  moduleId: string,
  events: unknown,
  version = curriculum.version,
) {
  const trainingModule = curriculumFor(version).modules.find((m) => m.id === moduleId);
  if (!trainingModule) throw new Error("Unknown module.");
  if (!Array.isArray(events) || events.length > trainingModule.questions.length)
    throw new Error("Invalid answer sequence.");
  const parsed = z.array(eventSchema).safeParse(events);
  if (!parsed.success) throw new Error("Invalid answer sequence.");
  const answers = parsed.data;
  let correct = 0;
  const criticalFailures: string[] = [];
  for (let i = 0; i < events.length; i++) {
    const e = answers[i],
      q = trainingModule.questions[i];
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
  const complete = events.length === trainingModule.questions.length,
    score = Math.floor((correct * 100) / trainingModule.questions.length);
  return {
    score,
    correct,
    total: trainingModule.questions.length,
    complete,
    valid: true,
    criticalFailures,
    passed: complete && score >= 80 && !criticalFailures.length,
  };
}
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export function validateImport(raw: unknown) {
  const parsed=exportSchema.safeParse(raw);
  if(!parsed.success)throw new Error("Invalid training export. Expected 1–100 completed attempts.");
  const input=parsed.data;
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
  const sector = input.worker.sector ?? "Unspecified";
  if (!["Unspecified", "Mining", "Steel", "Mica", "Other"].includes(sector))
    throw new Error("Invalid work sector.");
  const ids = new Set();
  const attempts = input.attempts.map((a) => {
    if (
      !a ||
      !uuid.test(a.id) ||
      ids.has(a.id) ||
      a.workerId !== input.worker.id ||
      !supported.some((c) => c.version === a.contentVersion) ||
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
    const result = grade(a.moduleId, a.events, a.contentVersion);
    if (
      a.events.some(
        (e, i) =>
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
      const first = a.events.findIndex((e) =>
        result.criticalFailures.includes(e.questionId),
      );
      if (first !== a.events.length - 1)
        throw new Error("Invalid answers after critical failure.");
    }
    return { ...a, result };
  });
  return { worker: { ...input.worker, sector }, attempts };
}
