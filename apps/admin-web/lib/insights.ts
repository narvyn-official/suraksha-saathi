import { curriculum, curriculumFor } from "./grading";
import { recordStatus } from "./validity";
export type TrainingRow = {
  id: string;
  worker_name: string;
  worker_id: string;
  worker_sector?: string;
  payload: {
    moduleId:string;kind:string;endedAt:number;contentVersion:string;mode?:string;startedAt?:number;
    result:{passed:boolean;score:number;total?:number;criticalFailures?:string[]};events:import("./grading").AnswerEvent[];
  };
};
export type CertificateRow = {
  id: string;
  attempt_id: string;
  revoked_at: number | null;
  issued_at: number;
  expiresAt?: number | null;
};
export function insights(
  records: TrainingRow[],
  certificates: CertificateRow[],
  now = Date.now(),
) {
  const workers = new Map<
    string,
    {
      id: string;
      name: string;
      sector: string;
      attempts: TrainingRow[];
      latest: Record<string, TrainingRow>;
      lastSeen: number;
    }
  >();
  const ordered = [...records].sort(
    (a, b) => b.payload.endedAt - a.payload.endedAt || a.id.localeCompare(b.id),
  );
  for (const r of ordered) {
    let w = workers.get(r.worker_id);
    if (!w) {
      w = {
        id: r.worker_id,
        name: r.worker_name || "Unnamed learner",
        sector: r.worker_sector || "Unspecified",
        attempts: [],
        latest: {},
        lastSeen: r.payload.endedAt,
      };
      workers.set(r.worker_id, w);
    }
    w.attempts.push(r);
    if (r.payload.kind === "assessment" && !w.latest[r.payload.moduleId])
      w.latest[r.payload.moduleId] = r;
  }
  const list = Array.from(workers.values()).map((w) => {
    const latest = Object.values(w.latest);
    const passed = latest.filter((r) => r.payload.result.passed).length;
    const needsPractice = latest.some((r) => !r.payload.result.passed);
    const activeCredentials = certificates.filter(
      (c) => recordStatus(c, now) === "active" && w.attempts.some((a) => a.id === c.attempt_id),
    ).length;
    return {
      ...w,
      passed,
      needsPractice,
      activeCredentials,
      status: needsPractice
        ? "Needs practice"
        : passed === curriculum.modules.length
          ? "All simulations passed"
          : passed
            ? "In progress"
            : "Not assessed",
    };
  });
  const modules = curriculum.modules.map((m) => {
    const rows = list.flatMap((w) => (w.latest[m.id] ? [w.latest[m.id]] : []));
    return {
      id: m.id,
      name:
        (
          {
            fire: "Fire",
            gas: "Gas / confined",
            machinery: "Machinery",
            ppe: "PPE",
            emergency: "Emergency",
          } as Record<string, string>
        )[m.id] ?? m.title[0],
      passed: rows.filter((r) => r.payload.result.passed).length,
      needsPractice: rows.filter((r) => !r.payload.result.passed).length,
      notAssessed: list.length - rows.length,
    };
  });
  const misses = new Map<
    string,
    {
      id: string;
      title: string;
      module: string;
      wrong: number;
      answered: number;
      critical: boolean;
    }
  >();
  // Use each learner's latest assessment in each module to avoid repeat-attempt inflation.
  for (const w of list)
    for (const row of Object.values(w.latest)) {
      const trainingModule = curriculumFor(row.payload.contentVersion).modules.find(
        (m) => m.id === row.payload.moduleId,
      );
      if (!trainingModule) continue;
      for (const e of row.payload.events) {
        const q = trainingModule.questions.find((q) => q.id === e.questionId);
        if (!q) continue;
        const item = misses.get(q.id) || {
          id: q.id,
          title: q.prompt[0],
          module: trainingModule.title[0],
          wrong: 0,
          answered: 0,
          critical: q.critical,
        };
        item.answered++;
        if (!q.options.find((o) => o.id === e.optionId)?.correct) item.wrong++;
        misses.set(q.id, item);
      }
    }
  return {
    workers: list,
    modules,
    hotspots: [...misses.values()]
      .filter((x) => x.wrong)
      .sort(
        (a, b) => Number(b.critical) - Number(a.critical) || b.wrong - a.wrong,
      )
      .slice(0, 6),
    needsPractice: list.filter((w) => w.needsPractice).length,
    fullyPassed: list.filter(
      (w) => w.passed === curriculum.modules.length && !w.needsPractice,
    ).length,
  };
}
export function workerCsv(workers: ReturnType<typeof insights>["workers"]) {
  const cell = (v: unknown) => {
    let s = String(v ?? "");
    if (/^[\s]*[=+\-@]/.test(s)) s = "'" + s;
    return '"' + s.replace(/"/g, '""') + '"';
  };
  return [
    [
      "Worker ID",
      "Name",
      "Sector",
      "Latest simulations passed",
      "Available modules",
      "Status",
      "Active pilot credentials",
      "Latest attempt",
    ],
    ...workers.map((w) => [
      w.id,
      w.name,
      w.sector,
      w.passed,
      curriculum.modules.length,
      w.status,
      w.activeCredentials,
      new Date(w.lastSeen).toISOString(),
    ]),
  ]
    .map((row) => row.map(cell).join(","))
    .join("\r\n");
}
