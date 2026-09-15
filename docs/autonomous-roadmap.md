# Suraksha Saathi — continuous development roadmap

Updated 15 September 2026. The user has authorized ongoing autonomous development and useful feature additions. Preserve the refined light theme and focus on workers using mid-range Android 10+ phones in Jharkhand. This document is the durable handoff between recurring development runs. Update it after each meaningful increment.

## Baseline

Public repository: https://github.com/narvyn-official/suraksha-saathi

Current implementation milestone: v0.5.6 recall challenge candidate. Public downloads now include v0.5.6-pilot as an explicitly unverified-camera prerelease; v0.5.1-pilot is held as a draft after user feedback on the AR experience. Five modules (fire, gas/confined spaces, machinery/isolation, PPE/exposure, emergency/reporting), 40 decisions, English/Hindi pilot text, native offline SQLite progress, ARCore anchors, detailed original 3D equipment with material lighting, local spaced retrieval review, staged component recognition, signed pilot credentials, and persistent trainer analytics are implemented. See build-status.md, validation.md and reference-benchmark.md for evidence and limitations. The local dashboard is http://localhost:5173/ when its development server is running.

## First work to pick up

**Latest engineering increment: 0.5.6.** [Remember, then do](ar-recall-challenge.md) adds optional hidden cues, recorded hints and a rehearsal debrief to fire/gas room missions; 128 JVM and 18 emulator methods passed. This is practice, not an independent rubric. Follow [AR technology and validation](ar-technology-and-validation.md) and [problem-statement coverage](ar-problem-statement-coverage.md). Full anchor-relative poses, camera texture lifecycle, asynchronous retirement, surface footprints and stable placement previews are implemented; physical verification remains pending. After device acceptance, integrate missing fire exit/evacuation and gas PPE/buddy objectives into the action-led room flow, then connect attributed journals to trainer review. Depth/marker features are staged proposals, not delivered capabilities.

**Current blocking priority (15 September): user reports AR does not work.** Earlier Samsung testing confirmed a camera feed but no usable tracking/placement, with delayed ARCore sensor poses and severe/critical thermal throttling. The current 0.5.6 candidate incorporates the earlier recovery work plus the documented pose, texture/session and placement corrections. Only an emulator is connected. Install the latest candidate when the phone reconnects and record actual three-station placement and both complete camera missions before claiming AR success. Preserve the previous failure history in [ar-recovery-validation.md](ar-recovery-validation.md).

**Screen timing follow-up resolved for the hardware QA setup:** profiling found graphics waits while the emulator used `-gpu swiftshader_indirect`. Restarting with `-gpu host` made the unchanged full fire/gas tests pass. Do not loosen continuity limits or keep retrying the old software graphics setup as if it represented a phone. The 0.5.4 candidate also caches immutable meshes on the GPU, fixes gas green-marker placement behind the barrier, and improves guidance while dragging the attendant. See [ar-rendering-and-placement.md](ar-rendering-and-placement.md). Real phone tracking/placement is still the first validation priority.

Current user steering prioritizes realistic equipment and evidence-informed learning. The v0.3.1 milestone adds camera-free staged component recognition: 12 visible parts, examples, hidden labels, two orientations, hints, text alternatives and an integrated local review queue. Due reviews start without examples; early clean repetition cannot postpone the due date. Assistance and mixed-mode exposure remain attributable. See component-practice.md, ar-learning-evidence.md and spaced-review.md for implemented scope and boundaries.

Continue realism through approved reference geometry, clearer component silhouettes and grounded contact/shadow cues. Investigate a maintainable authored asset pipeline before promising photorealism. Current models remain original procedural illustrations; real phone frame-time, lighting and tracking measurements are external validation gates.

**Camera component practice is implemented for fire in v0.4.1:** it reuses the catalogue and renderer, preserves screen/text alternatives, records presentation exposure, gates choices on fresh images and tracking, and rejects stale stage/lifecycle callbacks. See camera-component-practice.md and validation.md. Positive real-camera behavior remains an external test gate.

**AR decision recovery is implemented in v0.4.2:** fresh-image and revision guards now cover scored/guided decision stations. Saved practice explanations and assessment acknowledgements survive recreation until explicit Continue; native measured callouts reject unreadable placements. See ar-assessment-recovery.md.

**Scoped accessibility work is implemented in v0.4.3:** equipment inspection scrolls instead of collapsing in landscape, native controls include tilt and readable view state, text descriptions appear earlier, saved component feedback advances without tracking, and headings/pane titles/distinct review labels support assistive services. See accessibility.md and validation.md for measured layouts and service-facing action tests; actual TalkBack speech and worker usability remain external checks.

**Native placement is implemented in v0.4.4:** labeled centre actions, a visible aiming cross, tracked-plane hit tests and expiring viewport/revision requests supplement direct camera taps. Older rendering frames cannot discard newer requests, and pause disables eligibility before GL shutdown. Camera-off native action checks cover English/Hindi at normal and 200% system font. See ar-placement.md.

**Next software priority:** current user steering prioritizes physical AR validation and meaningful simulated actions. The 0.5.1 increment adds five attributed spatial target-hold steps; test these on the connected supported phone using ar-device-validation.md, then progress to continuous trajectories and replay-validated trainer procedure review. Keep practical sign-off separate. Continue to close the functional gaps from the user’s full-requirements audit. The 0.5 procedure prototype adds ordered fire/gas actions, visible equipment states, shared-phone profiles and signed expiry. Next implement reviewed, versioned localization-pack infrastructure and comprehensive Hindi string coverage; build Santali import/review tooling without inventing released translations. Then integrate replay-validated procedural evidence into trainer review, with explicit non-certification scope, and add assessor/organization authorization before practical sign-off. Continue improving actual equipment interaction and varied independent scenarios. Camera hardware, qualified content review and the original unresolved hosting registration are external gates; work independently on software while they remain unavailable.

The fifth emergency/reporting domain is implemented in v0.4.0 with eight decisions, six critical gates, an original station scene, component practice and trainer evidence support. Its general reporting/evacuation lessons are not first-aid or hazardous-rescue qualification. The original brief ended at “(3) Machinery”; the fourth/fifth domains remain product proposals under the user’s design freedom, not claims about an unseen official brief.

## Prioritized backlog

| Priority | Outcome | Completion evidence |
|---|---|---|
| 1 | Five-domain pilot implemented in v0.4.0 | Lessons, practice, critical-gated assessments, scenes, history, imports and signed pilot records; content and field approval remain separate |
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

- [x] Five pilot training domains with offline learning and recoverable assessments; content approval remains a separate gate.
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

- 2026-09-15: Completed the initial staged component-recognition flow, database version 3 migration and English/Hindi alternatives. Review fixed answer-position predictability, hint attribution, mixed-mode attribution and early-repeat spacing inflation. Later review rounds begin without examples. All 21 JVM tests and the combined 10-test emulator suite passed; actual rendered-buffer checks cover recreation. Fresh views were inspected and an emulator demo captured. This feature is camera-free and keeps assessment/credential evidence unchanged. Next: fifth emergency/reporting domain with primary-source content and explicit competent-review gates; then extend reviewed component interactions to the AR camera path with recovery checks.

- 2026-09-15: Added emergency/reporting as the fifth domain (40 total decisions, six new critical gates), original radio/closed aid-case/assembly scene, and three component targets (15 total). Preserved byte-identical 0.3 archives and unchanged earlier module content; compatible decision reviews retain their dates/streaks. Trainer support includes five-domain coverage, old-version replay, emergency issuance/verification and native signed-token verification. Source and test evidence are in validation.md. Next: bounded camera-based component practice with tracking/pause recovery.

- 2026-09-15: Added fire-only camera component practice with one shared learning session, screen/text fallback and additive presentation attribution. Review identified and corrected repeated-image freshness, cached pre-pause images, installation retry, viewport exposure and per-frame persistence overhead. Existing content/archives and credential paths are unchanged. All 32 JVM tests and four component/recovery emulator tests passed, with a final fallback recheck after the permission-settings shortcut. Fresh screens were inspected and a 40-second emulator recovery demo was captured. Validation details are recorded in validation.md. Next: harden the older scored AR decision stations using the same freshness and revision rules; real camera placement and performance remain unverified.

- 2026-09-15: Hardened guided/scored AR decision stations in v0.4.2: immutable render snapshots, fresh-image and revision guards, saved feedback with explicit Continue, neutral assessment acknowledgement and native measured callouts. Camera retry and light permission-off fallback remain usable alongside screen mode. All 42 JVM tests and six emulator tests passed; after correcting the visually detected black fallback, the six native tests passed again and a 39.287-second recovery demo was captured. Curriculum, equipment meshes, database and credential/web paths are unchanged. Next: a bounded large-font/screen-reader audit of equipment, component practice and delayed review; real AR hardware remains an external gate.

- 2026-09-15: Completed scoped accessibility fixes for v0.4.3. Baseline testing reproduced a 0-pixel equipment viewport and clipped actions at 200% system font in landscape. Scrollable inspection, native tilt/view-state feedback, early descriptions, headings/pane titles, distinct recall actions and tracking-independent saved-feedback advancement address the identified barriers. All 42 JVM tests, four new large-font accessibility-action tests and six normal-font regression tests passed. Fresh screens were inspected and a 110.116-second emulator walkthrough captured; actual TalkBack speech/worker usability are not claimed. Next: native camera-placement actions using existing tracked-plane and freshness guards, with screen/text alternatives preserved. Meshes, curriculum, database and credentials remain unchanged.

- 2026-09-15: Completed native placement controls for v0.4.4. Shared immutable requests and a revision-aware queue reject stale geometry/time/lifecycle work; read-only review found and corrected older-frame queue removal and pause-order races. New English/Hindi native actions pass camera-off tests at normal and 200% text, alongside existing AR recovery/component regressions. Meshes, curriculum, database and credentials remain unchanged. Next priority superseded by the user’s complete-requirements request: functional procedural training, profiles, expiry and role-coloured light UI.

- 2026-09-15: Implemented the 0.5 requirement-gap milestone: 12-step fire and 9-step gas procedural drafts with replayable journals and visible equipment state; guided retry/independent stop; screen/text alternatives; shared-phone profiles; explicit signed expiry/renewal semantics; and functional Mineral Light action colours. Read-only review found stale child screens and a shared export-cache collision; resume/recreation/mutation guards and request/worker-bound exports address them with native regression cases. The final validation record and public release identify tested scope. These procedures remain non-certifiable labelled actions, not validated hand technique. Next: localization coverage and reviewed-pack tooling, then trainer procedural evidence and assessor/organization controls; follow requirements-tracker.md rather than cosmetic-only work.

- 2026-09-15: Added five spatial target-hold steps, model-space ray selection, bounded replay-validated sample evidence and camera/screen separation in 0.5.1. Native testing reproduced fire screen-hold timing failures and a translated-button scrolling defect; static screen sampling and real button layout positions resolved them. Added interrupted-hold, unsafe-position, camera-unavailable and Hindi fallback tests. Installed a preview over 0.5.0 on a Samsung SM-S921B running Android 16 with AR services 1.56.262080393; positive camera behavior remains pending direct observation. See validation.md. Next: complete physical-phone camera checks, then continuous fire trajectories and trainer procedure-evidence review.

- 2026-09-15: User reported that the app was not targeting AR learning properly. Held release 0.5.1 as a draft and restored published README downloads to 0.5.0. The 0.5.2 candidate separates camera target touches from placement, adds direct-touch/centre input attribution, a dedicated camera workspace with fixed primary action, camera feedback scenes and specific tracking guidance. Phone 0.5.1 installation alone was not treated as AR validation. Next: observe candidate placement/target alignment on the connected phone, then develop continuous spatial task performance and trainer procedure review rather than additional quiz cards.

- 2026-09-15: Built the 0.5.2 room-mission candidate in response to feedback that the old experience felt like a quiz/model viewer. The primary fire/gas entry now opens three-station AR placement, gesture-led equipment actions and consequences, with separate non-certifying local journals. Added fresh viewport/phase and lifecycle gates, interrupted-gesture hints, bounded rendering, continuous fire sweep and outside-only gas response. Final complete screen-mission tests and 87 JVM checks passed; Hindi 200% portrait/landscape rendering was checked. Real-phone placement, camera gestures and device performance remain the next gate; public release links stay at 0.5.0 until validation. See room-ar-missions.md and validation.md.
