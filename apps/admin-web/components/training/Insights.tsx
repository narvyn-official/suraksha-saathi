"use client";
import { useMemo, useState } from "react";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid } from "recharts";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";
import {
  Table,
  TableHeader,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Empty,
  EmptyHeader,
  EmptyTitle,
  EmptyDescription,
} from "@/components/ui/empty";
import { Download, Users, AlertTriangle, CheckCircle2 } from "lucide-react";
import {
  insights,
  workerCsv,
  type TrainingRow,
  type CertificateRow,
} from "@/lib/insights";
import { curriculum } from "@/lib/grading";
export function TrainingInsights({
  records,
  certificates,
  onReview,
}: {
  records: TrainingRow[];
  certificates: CertificateRow[];
  onReview: (row: TrainingRow) => void;
}) {
  const data = useMemo(
    () => insights(records, certificates),
    [records, certificates],
  );
  return (
    <>
      <div className="insight-strip">
        <div>
          <Users />
          <strong>{data.workers.length}</strong>
          <span>Learners in these records</span>
        </div>
        <div>
          <AlertTriangle />
          <strong>{data.needsPractice}</strong>
          <span>Need further practice</span>
        </div>
        <div>
          <CheckCircle2 />
          <strong>{data.fullyPassed}</strong>
          <span>Passed all {curriculum.modules.length} simulations</span>
        </div>
      </div>
      <div className="analytics-grid">
        <section className="panel">
          <h2>Latest assessment by module</h2>
          <p>
            One latest assessment per worker and module. Repeat attempts do not
            inflate completion.
          </p>
          {data.workers.length ? (
            <>
              <ChartContainer
                className="h-[270px] w-full"
                config={{
                  passed: { label: "Passed", color: "#3157d5" },
                  needsPractice: { label: "Needs practice", color: "#c08627" },
                  notAssessed: { label: "Not assessed", color: "#dce4f2" },
                }}
              >
                <BarChart data={data.modules} accessibilityLayer>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="name" tickLine={false} axisLine={false} />
                  <YAxis
                    allowDecimals={false}
                    tickLine={false}
                    axisLine={false}
                    width={30}
                  />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <Bar
                    dataKey="passed"
                    stackId="a"
                    fill="var(--color-passed)"
                  />
                  <Bar
                    dataKey="needsPractice"
                    stackId="a"
                    fill="var(--color-needsPractice)"
                  />
                  <Bar
                    dataKey="notAssessed"
                    stackId="a"
                    fill="var(--color-notAssessed)"
                    radius={[5, 5, 0, 0]}
                  />
                </BarChart>
              </ChartContainer>
              <div className="chart-legend">
                <span>
                  <i style={{ background: "#3157d5" }} />
                  Passed
                </span>
                <span>
                  <i style={{ background: "#c08627" }} />
                  Needs practice
                </span>
                <span>
                  <i style={{ background: "#dce4f2" }} />
                  Not assessed
                </span>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Module</TableHead>
                    <TableHead>Passed</TableHead>
                    <TableHead>Needs practice</TableHead>
                    <TableHead>Not assessed</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.modules.map((m) => (
                    <TableRow key={m.id}>
                      <TableCell>{m.name}</TableCell>
                      <TableCell>{m.passed}</TableCell>
                      <TableCell>{m.needsPractice}</TableCell>
                      <TableCell>{m.notAssessed}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </>
          ) : (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>Import training to see coverage</EmptyTitle>
                <EmptyDescription>
                  The chart uses your workers' saved decisions.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
        </section>
        <section className="panel">
          <h2>Decisions to revisit</h2>
          <p>
            Missed decisions in each worker’s latest assessment. Critical
            decisions appear first.
          </p>
          {data.hotspots.length ? (
            data.hotspots.map((h) => (
              <div className="hotspot" key={h.id}>
                <span className={`badge ${h.critical ? "review" : ""}`}>
                  {h.critical ? "Critical decision" : "Knowledge check"}
                </span>
                <h3>{h.title}</h3>
                <p>
                  {h.module} · {h.wrong} of {h.answered} answered incorrectly
                </p>
              </div>
            ))
          ) : (
            <div className="quiet-state">
              <CheckCircle2 />
              <h3>
                {records.length
                  ? "No missed decisions in assessed answers"
                  : "No assessment evidence yet"}
              </h3>
              <p>
                {records.length
                  ? "Unanswered questions remain unassessed. This is not proof of practical competence."
                  : "Import a completed assessment to identify practice needs."}
              </p>
            </div>
          )}
        </section>
      </div>
      <section className="panel followup-panel">
        <h2>Practice follow-up</h2>
        {data.workers
          .filter((w) => w.needsPractice)
          .map((w) => (
            <div className="followup" key={w.id}>
              <div>
                <strong>{w.name}</strong>
                <p>
                  {w.sector} ·{" "}
                  {
                    Object.values(w.latest).filter(
                      (r) => !r.payload.result.passed,
                    ).length
                  }{" "}
                  module(s) need practice
                </p>
              </div>
              <Button
                variant="outline"
                onClick={() =>
                  onReview(
                    Object.values(w.latest).find(
                      (r) => !r.payload.result.passed,
                    )!,
                  )
                }
              >
                Review decisions
              </Button>
            </div>
          ))}
        {!data.needsPractice && (
          <p>No failed latest assessments in the available records.</p>
        )}
      </section>
    </>
  );
}
export function WorkerDirectory({
  records,
  certificates,
  onReview,
}: {
  records: TrainingRow[];
  certificates: CertificateRow[];
  onReview: (row: TrainingRow) => void;
}) {
  const data = useMemo(
    () => insights(records, certificates),
    [records, certificates],
  );
  const [search, setSearch] = useState(""),
    [sector, setSector] = useState("all"),
    [status, setStatus] = useState("all"),
    [selected, setSelected] = useState<string | null>(null);
  const filtered = data.workers.filter(
    (w) =>
      `${w.name} ${w.id}`.toLowerCase().includes(search.toLowerCase()) &&
      (sector === "all" || w.sector === sector) &&
      (status === "all" ||
        (status === "practice"
          ? w.needsPractice
          : status === "complete"
            ? w.status === "All simulations passed"
            : w.status === "Not assessed")),
  );
  const worker = data.workers.find((w) => w.id === selected);
  function exportCsv() {
    const url = URL.createObjectURL(
      new Blob([workerCsv(filtered)], { type: "text/csv;charset=utf-8" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "suraksha-worker-training.csv";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return (
    <>
      <section className="panel">
        <div className="section-heading">
          <div>
            <h2>Worker learning directory</h2>
            <p className="muted">
              {filtered.length} of {data.workers.length} workers · simulation
              evidence
            </p>
          </div>
          <Button
            variant="outline"
            onClick={exportCsv}
            disabled={!filtered.length}
          >
            <Download size={18} />
            Export CSV
          </Button>
        </div>
        <div className="worker-filters">
          <Input
            aria-label="Search worker"
            placeholder="Search name or worker ID"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <Select value={sector} onValueChange={setSector}>
            <SelectTrigger aria-label="Work sector">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {["all", "Mining", "Steel", "Mica", "Other", "Unspecified"].map(
                (x) => (
                  <SelectItem key={x} value={x}>
                    {x === "all" ? "All sectors" : x}
                  </SelectItem>
                ),
              )}
            </SelectContent>
          </Select>
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger aria-label="Training status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All training states</SelectItem>
              <SelectItem value="practice">Needs practice</SelectItem>
              <SelectItem value="complete">All simulations passed</SelectItem>
              <SelectItem value="unassessed">Not assessed</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {filtered.length ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Worker</TableHead>
                <TableHead>Sector</TableHead>
                <TableHead>Simulation coverage</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>History</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((w) => (
                <TableRow key={w.id}>
                  <TableCell>
                    <strong>{w.name}</strong>
                    <span className="table-sub">{w.id.slice(0, 8)}</span>
                  </TableCell>
                  <TableCell>{w.sector}</TableCell>
                  <TableCell>
                    {w.passed} / {curriculum.modules.length} passed
                  </TableCell>
                  <TableCell>
                    <span
                      className={`badge ${w.needsPractice ? "review" : w.passed ? "good" : ""}`}
                    >
                      {w.status}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" onClick={() => setSelected(w.id)}>
                      Open record
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <Empty>
            <EmptyHeader>
              <EmptyTitle>No matching workers</EmptyTitle>
              <EmptyDescription>
                Import records or adjust the filters.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        )}
      </section>
      <Dialog
        open={!!worker}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{worker?.name}</DialogTitle>
            <DialogDescription>
              {worker?.sector} · {worker?.id}
            </DialogDescription>
          </DialogHeader>
          {worker && (
            <>
              <div className="worker-summary">
                <span>
                  {worker.passed}/{curriculum.modules.length} latest simulations
                  passed
                </span>
                <span>{worker.activeCredentials} active pilot credentials</span>
              </div>
              {curriculum.modules.map((m) => {
                const r = worker.latest[m.id];
                return (
                  <div className="followup" key={m.id}>
                    <div>
                      <strong>{m.title[0]}</strong>
                      <p>
                        {r
                          ? `${r.payload.result.score}% · ${r.payload.result.passed ? "Passed" : "Needs practice"}`
                          : "Not assessed"}
                      </p>
                    </div>
                    {r && (
                      <Button
                        variant="outline"
                        onClick={() => {
                          setSelected(null);
                          onReview(r);
                        }}
                      >
                        Review
                      </Button>
                    )}
                  </div>
                );
              })}
              <h3 className="history-heading">All imported attempts</h3>
              {worker.attempts.map((a) => (
                <div className="followup" key={a.id}>
                  <div>
                    <strong>
                      {
                        curriculum.modules.find(
                          (m) => m.id === a.payload.moduleId,
                        )?.title[0]
                      }
                    </strong>
                    <p>
                      {a.payload.kind} ·{" "}
                      {new Date(a.payload.endedAt).toLocaleString()} ·{" "}
                      {a.payload.result.score}%
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setSelected(null);
                      onReview(a);
                    }}
                  >
                    View
                  </Button>
                </div>
              ))}
              <p className="fine">
                Practical assessment and identity verification are not recorded
                by this pilot.
              </p>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
