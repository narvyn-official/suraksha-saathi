"use client";
import { useEffect, useState, useCallback } from "react";
import {
  ShieldCheck,
  Upload,
  Flame,
  Wind,
  Wrench,
  Siren,
  GraduationCap,
  Users,
  BadgeCheck,
  LockKeyhole,
  Download,
  Search,
  CheckCircle2,
  FileCheck2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { RoomJournals } from "@/components/training/RoomJournals";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from "@/components/ui/alert-dialog";
import {
  Empty,
  EmptyHeader,
  EmptyTitle,
  EmptyDescription,
  EmptyMedia,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import QRCode from "qrcode";
import {
  TrainingInsights,
  WorkerDirectory,
} from "@/components/training/Insights";
import { curriculum, curriculumFor } from "@/lib/grading";
import { recordStatus, statusLabel, credentialStatus, validity } from "@/lib/validity";
type Row = {
  id: string;
  worker_name: string;
  worker_id: string;
  worker_sector?: string;
  payload: any;
};
type Credential = {
  id: string;
  attempt_id: string;
  token: string;
  issued_at: number;
  expiresAt: number | null;
  revoked_at: number | null;
  reason: string | null;
};
async function api(path: string, method = "GET", body?: unknown) {
  const r = await fetch(`/api/${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const d: any = await r.json();
  if (!r.ok) throw new Error(d.error || "Request failed");
  return d;
}
const title = (id: string) =>
  curriculum.modules.find((m) => m.id === id)?.title[0] ?? id;
function download(name: string, content: string, type = "application/json") {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export default function Home() {
  const [tab, setTab] = useState("insights");
  const [expiryInput, setExpiryInput] = useState("");
  const [clock, setClock] = useState(Date.now());
  useEffect(() => { const timer = setInterval(() => setClock(Date.now()), 30_000); return () => clearInterval(timer); }, []);
  const [records, setRecords] = useState<Row[]>([]),
    [credentials, setCredentials] = useState<Credential[]>([]),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [filter, setFilter] = useState(""),
    [selected, setSelected] = useState<Row | null>(null),
    [active, setActive] = useState<Credential | null>(null),
    [qr, setQr] = useState(""),
    [token, setToken] = useState(""),
    [verification, setVerification] = useState<any>(null),
    [revoke, setRevoke] = useState<Credential | null>(null),
    [reason, setReason] = useState("");
  const [coverage, setCoverage] = useState({
    returned: 0,
    total: 0,
    truncated: false,
  });
  const load = useCallback(async () => {
    const d = await api("records");
    setRecords(d.attempts);
    if (d.coverage) setCoverage(d.coverage);
    setCredentials(d.credentials);
  }, []);
  useEffect(() => {
    load()
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [load]);
  useEffect(() => {
    let alive = true;
    setQr("");
    if (active)
      QRCode.toDataURL(`SURAKSHA:CREDENTIAL:${active.token}`, {
        width: 360,
        margin: 4,
        errorCorrectionLevel: "M",
      })
        .then((url) => {
          if (alive) setQr(url);
        })
        .catch(() =>
          setError(
            "Could not draw credential QR. Download the credential text instead.",
          ),
        );
    return () => {
      alive = false;
    };
  }, [active]);
  const verifyToken = useCallback(async (value: string) => {
    setTab("verify");
    setToken(value);
    setVerification(null);
    const result = await api("verify", "POST", { token: value });
    setVerification(result);
    return result;
  }, []);
  useEffect(() => {
    const context = (document as any).modelContext;
    if (!context?.registerTool) return;
    const lifecycle = new AbortController();
    Promise.resolve(
      context.registerTool(
        {
          name: "verify_training_credential",
          title: "Verify pilot training credential",
          description:
            "Verify a credential signature and current workspace status, updating the visible verification result. Does not issue a certificate.",
          inputSchema: {
            type: "object",
            properties: { token: { type: "string", maxLength: 6000 } },
            required: ["token"],
            additionalProperties: false,
          },
          annotations: { readOnlyHint: true, untrustedContentHint: true },
          execute: async (input: any) => {
            if (typeof input?.token !== "string" || input.token.length > 6000)
              throw new Error("Invalid credential text");
            return verifyToken(input.token);
          },
        },
        { signal: lifecycle.signal },
      ),
    ).catch(() => {});
    return () => lifecycle.abort();
  }, [verifyToken]);
  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await fn();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function importFile(file: File | undefined) {
    if (!file) return;
    await run(async () => {
      if (file.size > 1000000)
        throw new Error("Choose a JSON export smaller than 1 MB.");
      const result = await api("import", "POST", JSON.parse(await file.text()));
      await load();
      setMessage(
        `${result.imported} attempt(s) imported. ${result.unchanged} already saved.`,
      );
    });
  }
  async function issue(row: Row) {
    await run(async () => {
      const existing = credentials.find((c) => c.attempt_id === row.id);
      if (existing) { setActive(existing); setSelected(null); return; }
      const expiresAt = new Date(expiryInput).getTime();
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) throw new Error("Choose a future expiry approved by your site's training policy.");
      const result = await api("credentials", "POST", { attemptId: row.id, expiresAt });
      await load();
      setActive(result);
      setExpiryInput("");
      setSelected(null);
      setMessage("Pilot simulation credential issued.");
    });
  }
  useEffect(() => { setExpiryInput(""); }, [selected?.id]);
  const verificationStatus = verification ? credentialStatus(validity(verification, clock), verification.revocationStatus === "revoked" ? 0 : null, verification.revocationStatus !== "unknown") : "";
  const matches = records.filter((r) =>
    `${r.worker_name} ${r.worker_id} ${title(r.payload.moduleId)}`
      .toLowerCase()
      .includes(filter.toLowerCase()),
  );
  const passed = records.filter(
    (r) => r.payload.kind === "assessment" && r.payload.result.passed,
  ).length;
  return (
    <>
      <header className="topbar">
        <div className="brand">
          <span className="brand-icon">
            <ShieldCheck />
          </span>
          <strong>Suraksha Saathi</strong>
          <span className="brand-divider">Training centre</span>
        </div>
        <span className="privacy">
          <LockKeyhole size={16} /> Trainer workspace
        </span>
      </header>
      <main className="workspace">
        <div className="page-heading">
          <div>
            <p className="eyebrow">WORKER SAFETY · JHARKHAND</p>
            <h1>Training overview</h1>
            <p className="muted">
              Review learning records and verify pilot credentials.
            </p>
          </div>
          <Button variant="outline" disabled={busy} onClick={() => run(load)}>
            Refresh records
          </Button>
        </div>
        <div className="stats">
          {[
            {
              icon: Users,
              title: "Workers",
              value: new Set(records.map((r) => r.worker_id)).size,
              desc: "With imported training records",
            },
            {
              icon: GraduationCap,
              title: "Completed attempts",
              value: records.length,
              desc: `${passed} passed assessments`,
            },
            {
              icon: BadgeCheck,
              title: "Active pilot credentials",
              value: credentials.filter((c) => recordStatus(c, clock) === "active").length,
              desc: "Simulation learning only",
            },
          ].map((s) => (
            <div className="stat" key={s.title}>
              <div className="stat-label">
                <span>{s.title}</span>
                <s.icon size={21} />
              </div>
              {loading ? (
                <Skeleton className="my-4 h-9 w-12" />
              ) : (
                <strong>{s.value}</strong>
              )}
              <p>{s.desc}</p>
            </div>
          ))}
        </div>
        {error && (
          <div role="alert" className="notice error">
            {error}{" "}
            {error.includes("Sign in") ? (
              <a href="/signin-with-chatgpt?return_to=/">Sign in</a>
            ) : (
              <Button variant="caution" onClick={() => run(load)}>
                Retry loading
              </Button>
            )}
          </div>
        )}
        {message && (
          <div role="status" className="notice success">
            <CheckCircle2 size={19} />
            {message}
          </div>
        )}
        {coverage.truncated && (
          <div className="notice">
            Showing the latest {coverage.returned} of {coverage.total} imported
            attempts. Analytics and worker histories cover this subset.
          </div>
        )}
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList className="mb-6 h-auto min-h-12 flex-wrap bg-[#e9edf5]">
            <TabsTrigger value="insights" className="px-5">
              Overview
            </TabsTrigger>
            <TabsTrigger value="workers" className="px-5">
              Workers
            </TabsTrigger>
            <TabsTrigger value="records" className="px-5">
              Training records
            </TabsTrigger>
            <TabsTrigger value="room-practice" className="px-5">
              Practice journals
            </TabsTrigger>
            <TabsTrigger value="credentials" className="px-5">
              Credentials
            </TabsTrigger>
            <TabsTrigger value="curriculum" className="px-5">
              Curriculum
            </TabsTrigger>
            <TabsTrigger value="verify" className="px-5">
              Verify
            </TabsTrigger>
          </TabsList>
          <TabsContent value="insights">
            {loading ? (
              <Skeleton className="h-72 w-full" />
            ) : (
              <TrainingInsights
                records={records}
                certificates={credentials}
                now={clock}
                onReview={setSelected}
              />
            )}
          </TabsContent>
          <TabsContent value="workers">
            <WorkerDirectory
              records={records}
              certificates={credentials}
                now={clock}
              onReview={setSelected}
            />
          </TabsContent>
          <TabsContent value="room-practice"><RoomJournals /></TabsContent>
          <TabsContent value="records">
            <div className="dashboard-grid">
              <section className="panel records-panel">
                <div className="section-heading">
                  <h2>Learning records</h2>
                  <span className="count">{records.length}</span>
                </div>
                {records.length > 0 && (
                  <div className="search">
                    <Search size={18} />
                    <Input
                      aria-label="Find worker or module"
                      value={filter}
                      onChange={(e) => setFilter(e.target.value)}
                      placeholder="Find worker or module"
                    />
                  </div>
                )}
                {loading ? (
                  <Skeleton className="h-48 w-full" />
                ) : matches.length ? (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Worker / lesson</TableHead>
                        <TableHead>Outcome</TableHead>
                        <TableHead className="text-right">Review</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {matches.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell>
                            <strong>
                              {r.worker_name || "Unnamed learner"}
                            </strong>
                            <span className="table-sub">
                              {title(r.payload.moduleId)}
                            </span>
                            <span className="table-meta">
                              {r.payload.mode === "arcore"
                                ? "Camera AR"
                                : r.payload.mode === "hybrid"
                                  ? "AR + screen"
                                  : "On-screen"}{" "}
                              ·{" "}
                              {new Date(r.payload.endedAt).toLocaleDateString()}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span
                              className={`badge ${r.payload.result.passed ? "good" : "review"}`}
                            >
                              {r.payload.kind === "practice"
                                ? "Practice"
                                : r.payload.result.passed
                                  ? "Passed"
                                  : "Needs practice"}
                            </span>
                            <span className="table-sub">
                              {r.payload.result.score}% ·{" "}
                              {r.payload.events.length}/{r.payload.result.total}{" "}
                              decisions
                            </span>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              variant="ghost"
                              onClick={() => setSelected(r)}
                            >
                              View
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : (
                  <Empty className="min-h-72">
                    <EmptyHeader>
                      <EmptyMedia variant="icon">
                        <GraduationCap />
                      </EmptyMedia>
                      <EmptyTitle>
                        {filter
                          ? "No matching records"
                          : "Bring learning into view"}
                      </EmptyTitle>
                      <EmptyDescription>
                        {filter
                          ? "Try another worker name or lesson."
                          : "Import a worker’s training file to review their decisions and assessment results."}
                      </EmptyDescription>
                    </EmptyHeader>
                  </Empty>
                )}
              </section>
              <section className="panel import-panel">
                <span className="feature-icon">
                  <Upload />
                </span>
                <h2>Import offline training</h2>
                <p>
                  On the worker’s phone, open{" "}
                  <strong>My record → Export records for trainer.</strong> Then
                  choose that file here.
                </p>
                <label htmlFor="training-file" className="field-label">
                  Training file (.json)
                </label>
                <Input
                  id="training-file"
                  type="file"
                  accept="application/json,.json"
                  disabled={busy}
                  onChange={(e) => {
                    void importFile(e.target.files?.[0]);
                    e.target.value = "";
                  }}
                  className="h-12 bg-white"
                />
                <p className="fine">
                  Results are recalculated from recorded answers. A critical
                  unsafe decision cannot be averaged away.
                </p>
                <div className="import-note">
                  <FileCheck2 size={20} />
                  <span>
                    Repeat imports are safe. Existing records are checked before
                    saving.
                  </span>
                </div>
              </section>
            </div>
          </TabsContent>
          <TabsContent value="credentials">
            <section className="panel">
              <h2>Pilot credentials</h2>
              {credentials.length ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Credential</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {credentials.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          <strong>
                            {records.find((r) => r.id === c.attempt_id)
                              ?.worker_name || "Learner"}
                          </strong>
                          <span className="table-sub">
                            {c.id.slice(0, 8)} ·{" "}
                            {new Date(c.issued_at).toLocaleDateString()}
                          </span>
                          <span className="table-sub">
                            {c.expiresAt == null ? "No expiry recorded" : `Expires ${new Date(c.expiresAt).toLocaleString()}`}
                          </span>
                        </TableCell>
                        <TableCell>
                          <span
                            className={`badge ${recordStatus(c, clock) === "active" ? "good" : "review"}`}
                          >
                            {statusLabel(recordStatus(c, clock))}
                          </span>
                        </TableCell>
                        <TableCell>
                          <Button variant="ghost" onClick={() => setActive(c)}>
                            View QR
                          </Button>
                          {!c.revoked_at && (
                            <Button
                              variant="destructive"
                              onClick={() => {
                                setReason("");
                                setRevoke(c);
                              }}
                            >
                              Revoke
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty>
                  <EmptyHeader>
                    <EmptyMedia variant="icon">
                      <BadgeCheck />
                    </EmptyMedia>
                    <EmptyTitle>No credentials yet</EmptyTitle>
                    <EmptyDescription>
                      Review a passed assessment to issue a signed pilot
                      simulation credential.
                    </EmptyDescription>
                  </EmptyHeader>
                </Empty>
              )}
            </section>
          </TabsContent>
          <TabsContent value="curriculum">
            <div className="dashboard-grid">
              {curriculum.modules.map((m, i) => (
                <section className="panel" key={m.id}>
                  <span className="feature-icon">
                    {m.id === "fire" ? (
                      <Flame />
                    ) : m.id === "gas" ? (
                      <Wind />
                    ) : m.id === "machinery" ? (
                      <Wrench />
                    ) : m.id === "emergency" ? (
                      <Siren />
                    ) : (
                      <ShieldCheck />
                    )}
                  </span>
                  <h2>{m.title[0]}</h2>
                  <p>{m.subtitle[0]}</p>
                  <ul className="objectives">
                    {m.objectives.map((o) => (
                      <li key={o[0]}>{o[0]}</li>
                    ))}
                  </ul>
                  <span className="pill">
                    {m.questions.length} decisions · English & Hindi draft
                  </span>
                </section>
              ))}
            </div>
            <p className="scope">
              Content v{curriculum.version} awaits a competent safety reviewer.
              Santali awaits native review.
            </p>
          </TabsContent>
          <TabsContent value="verify">
            <section className="panel verify-panel">
              <h2>Verify a pilot credential</h2>
              <p>Paste the signed text from a Suraksha Saathi credential QR.</p>
              <label htmlFor="credential" className="field-label">
                Credential text
              </label>
              <Input
                id="credential"
                value={token}
                onChange={(e) => {
                  setToken(e.target.value);
                  setVerification(null);
                }}
                placeholder="SURAKSHA:CREDENTIAL:…"
              />
              <Button
                disabled={busy || !token.trim()}
                className="mt-4"
                onClick={() =>
                  run(async () => {
                    setVerification(null);
                    await verifyToken(token);
                  })
                }
              >
                Verify credential
              </Button>
              {verification && (
                <div
                  className={`verification ${verificationStatus === "active" ? "success" : "error"}`}
                  role="status"
                >
                  <h3>
                    {statusLabel(verificationStatus)}
                  </h3>
                  <p>
                    {title(verification.moduleId)} · {verification.score}%
                  </p>
                  <p>
                    {verification.reason ||
                      verification.message ||
                      "Current status checked in this workspace."}
                  </p>
                  <p>{verification.expiresAt == null ? "No expiry recorded; current validity is not established." : `Recorded expiry: ${new Date(verification.expiresAt).toLocaleString()}`}</p>
                  <p>Signature verified. Practical observation: not assessed.</p>
                </div>
              )}
            </section>
          </TabsContent>
        </Tabs>
        <footer className="scope">
          <ShieldCheck size={18} />
          <span>
            Pilot records describe simulation learning. Practical competence and
            permission to work require separate assessment.
          </span>
        </footer>
      </main>
      <Dialog
        open={!!selected}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Review assessment decisions</DialogTitle>
            <DialogDescription>
              {selected?.worker_name || "Learner"} ·{" "}
              {title(selected?.payload.moduleId || "")}
            </DialogDescription>
          </DialogHeader>
          {selected && (
            <>
              <div className="review-summary">
                <strong>{selected.payload.result.score}%</strong>
                <span>
                  {selected.payload.kind === "practice"
                    ? "Guided practice"
                    : selected.payload.result.passed
                      ? "Simulation passed"
                      : "More practice needed"}
                  <br />
                  Practical observation: not assessed
                </span>
              </div>
              {selected.payload.events.map((e: any, i: number) => {
                const q = curriculumFor(selected.payload.contentVersion)!
                  .modules.find((m) => m.id === selected.payload.moduleId)!
                  .questions.find((q) => q.id === e.questionId)!;
                const o = q.options.find((o) => o.id === e.optionId)!;
                return (
                  <div className="decision" key={e.questionId}>
                    <h3>
                      {i + 1}. {q.prompt[0]}
                    </h3>
                    <p className={o.correct ? "correct" : "incorrect"}>
                      {o.correct ? "✓" : "!"} {o.text[0]}
                    </p>
                    <p>{q.explanation[0]}</p>
                  </div>
                );
              })}
              {selected.payload.kind === "assessment" &&
                selected.payload.result.passed && (
                  <>
                    <p className="fine">
                      Issue a pilot simulation credential. This does not certify
                      identity, practical competence or statutory compliance.
                    </p>
                    {credentials.some((c) => c.attempt_id === selected.id) ? (
                      <p className="fine">This assessment already has a credential. Renewal requires a new passed assessment; an existing expiry cannot be extended.</p>
                    ) : (
                      <>
                        <label htmlFor="credential-expiry" className="field-label">Expiry date and time · your local time</label>
                        <Input id="credential-expiry" type="datetime-local" value={expiryInput} onChange={(e) => setExpiryInput(e.target.value)} required aria-describedby="expiry-policy" />
                        <p id="expiry-policy" className="fine">Use the date approved in your site's training policy. No statutory validity period is assumed.</p>
                      </>
                    )}
                    <Button disabled={busy || (!credentials.some((c) => c.attempt_id === selected.id) && !expiryInput)} onClick={() => issue(selected)}>
                      {credentials.some((c) => c.attempt_id === selected.id) ? "View existing credential" : "Issue pilot credential"}
                    </Button>
                  </>
                )}
            </>
          )}
        </DialogContent>
      </Dialog>
      <Dialog
        open={!!active}
        onOpenChange={(open) => {
          if (!open) setActive(null);
        }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {active?.revoked_at
                ? "Revoked pilot credential"
                : "Pilot simulation credential"}
            </DialogTitle>
            <DialogDescription>
              Signed learning evidence · practical observation not assessed
            </DialogDescription>
          </DialogHeader>
          {active && <p role="status">{statusLabel(recordStatus(active, clock))}<br />{active.expiresAt == null ? "No expiry recorded" : `Expires ${new Date(active.expiresAt).toLocaleString()}`}</p>}
          {qr ? (
            <img
              className="credential-qr"
              src={qr}
              width={300}
              height={300}
              alt="Signed pilot credential QR"
            />
          ) : (
            <Skeleton className="mx-auto h-64 w-64" />
          )}
          <p className="fine">
            Scan with the Android app to verify the signature offline. Offline
            verification cannot confirm revocation.
          </p>
          <Button
            onClick={() =>
              active &&
              download(
                `suraksha-credential-${active.id.slice(0, 8)}.txt`,
                `SURAKSHA:CREDENTIAL:${active.token}`,
                "text/plain",
              )
            }
          >
            <Download size={18} />
            Download credential text
          </Button>
          {qr && (
            <Button
              variant="outline"
              onClick={() => {
                const a = document.createElement("a");
                a.href = qr;
                a.download = "suraksha-pilot-qr.png";
                a.click();
              }}
            >
              Save QR image
            </Button>
          )}
        </DialogContent>
      </Dialog>
      <AlertDialog
        open={!!revoke}
        onOpenChange={(open) => {
          if (!open) setRevoke(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Revoke this pilot credential?</AlertDialogTitle>
            <AlertDialogDescription>
              The signed QR remains readable offline, but this dashboard will
              show it as revoked. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <label htmlFor="reason" className="field-label">
            Reason
          </label>
          <Input
            id="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={500}
          />
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={busy || reason.trim().length < 5}
              onClick={() =>
                run(async () => {
                  await api("credentials", "PATCH", { id: revoke!.id, reason });
                  await load();
                  setRevoke(null);
                  setMessage("Credential revoked.");
                })
              }
            >
              Revoke credential
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
