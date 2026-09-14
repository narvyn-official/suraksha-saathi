# Suraksha Saathi — continuous development roadmap

Updated 14 September 2026. The user has authorized ongoing autonomous development and useful feature additions. Preserve the refined light theme and focus on workers using mid-range Android 10+ phones in Jharkhand. This document is the durable handoff between recurring development runs. Update it after each meaningful increment.

## Baseline

Public repository: https://github.com/narvyn-official/suraksha-saathi

Current tested milestone: v0.3.0-pilot (source identified by the release tag). Four modules (fire, gas/confined spaces, machinery/isolation, PPE/exposure), 32 decisions, English/Hindi pilot text, native offline SQLite progress, ARCore anchors, detailed original 3D equipment with material lighting, local spaced retrieval review, signed pilot credentials, and persistent trainer analytics are implemented. See build-status.md, validation.md and reference-benchmark.md for evidence and limitations. The local dashboard is http://localhost:5173/ when its development server is running.

## First work to pick up

Current user steering prioritizes realistic equipment and evidence-informed learning. The v0.3 milestone adds the fourth PPE/exposure domain, detailed equipment renderer and local spaced-review loop. Next implement staged component inspection: guided labels, independent component selection, explanation and an orientation change. Link this to the existing review schedule without changing certification evidence. See ar-learning-evidence.md and spaced-review.md for research and boundaries.

Continue realism through approved reference geometry, clearer component silhouettes and grounded contact/shadow cues. Investigate a maintainable authored asset pipeline before promising photorealism. Current models remain original procedural illustrations; real phone frame-time, lighting and tracking measurements are external validation gates.

Then implement the fifth proposed domain: emergency reporting, evacuation and safe response around an injured coworker. Keep guidance appropriate to untrained workers; do not teach hazardous rescue or substitute for practical first-aid instruction. The original brief ended at “(3) Machinery”; the fourth/fifth domains are product proposals under the user’s design freedom, not claims about an unseen official brief.

## Prioritized backlog

| Priority | Outcome | Completion evidence |
|---|---|---|
| 1 | All five domains integrated | Lessons, practice, critical-gated assessments, relevant scenes, history, imports and signed pilot records work for each module |
| 2 | Adaptive practice and recall | Missed decisions feed a targeted practice queue; spaced review uses persistent dates; practice never silently counts as certification |
| 3 | Richer AR interaction | Meaningful ordered actions and spatial interactions beyond answer cards; screen alternatives; interruption/camera/tracking recovery; real hardware measurements recorded separately |
| 4 | Worker-friendly onboarding and accessibility | Large touch targets, plain language, readable Hindi, screen-reader labels, font-scaling checks, reduced-motion support where needed, clear offline/permission states |
| 5 | Localization pipeline | String coverage checks, versioned language packs, local audio support and review metadata; Santali only marked released after native review |
| 6 | Trainer practical assessment | Traceable observations with assessor identity, scope and dates; simulation results remain distinct from practical sign-off and statutory approval |
| 7 | Organizations and access | Explicit authenticated roles, authorization tests, scoped worker data, audit trail, retention/export controls and safe revocation management |
| 8 | Secure sync and production hosting | Authenticated transfer, retry/idempotency/conflict handling, issuer secret provisioning, revocation freshness, key rotation and deployment recovery |
| 9 | Measured device quality | Android 10 and current API checks; physical mid-range ARCore phone testing; permission/lifecycle/offline recovery; startup, storage, frame-time and battery observations |
| 10 | Submission-ready delivery | Tested installable APK, reproducible source, accurate current demo, clear setup, reviewer evidence and honest feature matrix |

Choose a smaller higher-value fix ahead of this order when a reproducible defect blocks learning, privacy, correctness or usability. Add other features only when they have a clear worker or trainer benefit and a testable outcome. Avoid decorative churn, fabricated metrics and placeholder functionality presented as complete.

## Working loop

1. Read current instructions, Git status, this roadmap, build status and the most recent validation evidence. Inspect active work before editing; preserve unrelated or unfinished changes.
2. Select a cohesive, bounded improvement. State a material assumption if needed. Continue independent work when hardware, hosting or human review is unavailable.
3. Implement it across affected interfaces, persistence, content versions and documentation. Keep assessments deterministic and evidence attributable to the curriculum actually used.
4. Run relevant checks; inspect rendered UI when it changes. Never run competing Android instrumentation sessions or overlap Gradle device installation with direct instrumentation.
5. Fix discovered regressions. Record exactly what passed, failed or remains unverified. Update this roadmap with the next concrete step.
6. Commit focused verified changes. Push tested milestones to the existing public repository. Publish pilot APK/demo releases when the change merits a release, rather than issuing a release for every small edit.

## Release acceptance criteria

- [ ] Five usable, coherent training domains with offline learning and recoverable assessments.
- [ ] Critical unsafe actions cannot be averaged away; assessment/practice/practical-observation scopes stay distinct.
- [ ] Relevant original scenes and AR actions, with usable alternatives on unsupported phones.
- [ ] English/Hindi terminology approved by competent reviewers; native-reviewed Santali text/audio available.
- [ ] Accessible light UI verified with target text sizes and representative workers.
- [ ] Authenticity, authorization, audit, sync and credential revocation behave correctly under failure and replay.
- [ ] Physical Android 10+ and mid-range ARCore device evidence, including interrupted/offline use.
- [ ] Hosted trainer environment and production signing/distribution are verified.
- [ ] Current demo, setup instructions, validation report and public repository accurately match the release.
- [ ] No unresolved critical learning, privacy, data-integrity or reliability defects; limitations explicitly disclosed.

“Perfect” is the user's quality ambition, not a measurable guarantee. Report progress against these criteria. Once met, maintain the verified release and address regressions or user feedback rather than inventing endless work.

## External gates and constraints

- Native Santali and industrial safety review require qualified people; no automated translation is equivalent to approval.
- Real phone tests require actual suitable devices. Emulator or screen recordings do not prove real AR tracking.
- Existing Sites registration failed, and discovery found no site. Reconcile that original outcome through supported tools; do not duplicate registration or invent a hosted URL.
- Keep issuer private keys, runtime secrets, signing keystores, real worker records and unrelated personal data out of Git and release assets.
- Paid resources, contacting people and unrelated server access require relevant authorization. The user's saved SSH/email references do not authorize their use for this project.
- Preserve old content archives and matching trust files. Do not rotate the existing issuer merely to get a fresh setup working.

## Run log

- 2026-09-14: Enabled recurring continuation in this task and established this roadmap. Baseline v0.2 remains the latest released application. Next implementation: integrated PPE/exposure domain.

- 2026-09-14: Completed v0.3 implementation and validation: PPE brings coverage to four domains/32 decisions; detailed geometry and material lighting work in the shared viewer/AR renderer; offline spaced review persists separately from assessment evidence. Twelve JVM, six web and seven emulator tests passed, with web build/API checks and visual equipment review. Added primary-source learning research and a practical-transfer study plan. Next: staged component identification with fading guidance and changed orientation.
