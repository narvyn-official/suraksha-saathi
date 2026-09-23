# SurakshaAr

A **v0.10.0 local pilot** for Android safety learning and trainer review, built for Jharkhand’s industrial workforce with fixed screens and a light interface.

**Current source: 0.10.0 — local P0–P2 learning journey release.** Learner enrolment, MFA, course progress, five expanded scenario sequences, encrypted retry sync, instructor observations and an independent certification correction/review flow are implemented. See [local setup, changes and verification](docs/p0-p2-local-implementation.md). The downloadable APK is published as a pilot prerelease; account hosting remains local.

**Latest published download: [SurakshaAr v0.10.0 pilot prerelease](https://github.com/narvyn-official/suraksha-saathi/releases/tag/v0.10.0-pilot)**

- [Download the Android APK — SurakshaAr v0.10.0](https://github.com/narvyn-official/suraksha-saathi/releases/download/v0.10.0-pilot/surakshaar-0.10.0-debug.apk) · Android 10+, debug-signed pilot.
- [Watch the earlier v0.6.0 emulator walkthrough](https://github.com/narvyn-official/suraksha-saathi/releases/download/v0.6.0-pilot/suraksha-saathi-0.6.0-emulator-demo.mp4) · Screen fire/gas and explosion-evacuation practice; not physical AR evidence.
- [Download SHA-256 checksums](https://github.com/narvyn-official/suraksha-saathi/releases/download/v0.10.0-pilot/SHA256SUMS-0.10.0.txt).

**Previously published (0.7.1):** fixed learner/admin screens with explicit page controls and focused popups, automatic English/Hindi instructions, corrected Android Back navigation, detailed fire-type/agent learning and improved AR surface placement with a 20 cm virtual-footprint gap. Samsung phone checks confirm tracking, placement and automatic Hindi speech; earlier fire-evacuation and outside-only gas branches completed on the same phone. See [0.7.1 changes and validation](docs/release-0.7.1.md). **Public account hosting remains pending; native administration requires a configured account server.**

**In the published 0.6.0 version:** 0.6.0 expands fire room practice through exit selection, evacuation, assembly and reporting, adds an explosion-risk evacuation branch and gas PPE/buddy checks, introduces paged native lessons and saved room-practice recovery, and connects separate room journals to trainer review. All four camera paths share camera configuration, pacing, thermal recovery and session-release handling. See [implementation, verification and remaining gaps](docs/release-0.6.0.md). The latest physical checks and remaining scope are in the 0.7.1 release report; this remains a pilot prerelease.

The [access and certification follow-up](docs/access-certification-review-2026-09-20.md) fixes credential sharing/import and revoked-workspace recovery, records the earlier findings; the [0.9.0 government demonstration guide](docs/government-demo-0.9.0.md) implements the selected approval model.

## What you can use now

- Android app with English and Hindi lessons, guided practice and assessments for fire response, gas/confined spaces, machinery/isolation, PPE/exposure and emergency/reporting (40 decisions).
- Offline SQLite progress, interrupted-attempt recovery, critical-error assessment gates, installed offline voice support and PDF completion receipts.
- Fire/gas room missions led by equipment gestures and scene consequences, with camera and screen records kept separate. See [mission scope](docs/room-ar-missions.md).
- Original inspectable illustrations, detailed material-lit offline rotatable 3D equipment and ARCore camera mode with tracked equipment and decision cards on supported phones; screen practice on other Android 10+ phones.
- Trainer dashboard with a worker directory, sector/status filters, latest-assessment charts, practice follow-up, CSV export, persistent records, answer-by-answer review, signed pilot credentials, QR downloads, signature checks and revocation.
- Offline decision recall with corrective feedback, self-explanation prompts and scheduled return visits that survive compatible curriculum updates; assessment evidence remains unchanged.
- Staged equipment identification: examples, hidden labels, changed viewing angles, English/Hindi description alternatives and local review scheduling. Fire has optional camera-based component practice with tracking gates, preserved screen fallback and separate presentation attribution. Camera component recognition still needs its own physical validation.
- AR decision recovery preserves saved practice explanations and assessment acknowledgements until explicit Continue; fresh camera images, tracked placement and readable native answer controls gate new answers. [Recovery details](docs/ar-assessment-recovery.md).
- Paged equipment inspection with native rotation/tilt/zoom controls, meaningful accessibility headings and distinct review actions. Text-based component practice remains separately attributed. See [accessibility scope](docs/accessibility.md).
- Native centre-placement actions and a camera aiming cross for fire component practice and AR decisions, with fresh-frame/revision guards and screen alternatives. [Placement scope and limits](docs/ar-placement.md).
- Ordered fire/gas procedure drafts with persistent action journals and visible pin, nozzle, boundary and attendant states; camera/screen/text alternatives. [Procedure scope](docs/procedure-training.md).
- Shared-phone learner profiles with separate local progress and wallets.
- Android credential wallet with pinned-issuer signature verification offline, explicit expiry and legacy-date handling; renewal requires a new passed assessment.
- Role-coloured Mineral Light controls with native ripple/focus feedback and a responsive trainer dashboard.

**Pilot scope:** these are simulation learning records, not statutory safety certificates, identity verification, practical competence or permission to work. Santali is visibly pending native review. Tracking and station placement have been checked on one Samsung; full extinguisher discharge and a mid-range device matrix remain pending.

## Build Android

Install Android SDK platform/build tools 36 and a compatible JDK, then:

```sh
bash scripts/build-android.sh
```

The debug-signed APK is copied to `artifacts/surakshaar-0.10.0-debug.apk`. Android Studio can open `apps/android`. The repository includes the Gradle wrapper. For a fresh clone, generate the pilot issuer before building if you want your own independently approved pilot credentials:

```sh
node scripts/create-pilot-issuer.mjs
```

The command creates an ignored private runtime key and matching public trust files. It refuses to overwrite an existing private key. Never publish `.dev.vars` or signing keystores.

## Login and administration

The web dashboard now includes a sign-in screen, shared workspaces, Admin/Trainer/Viewer/Certifier roles, staff invitations, worker registration/editing, bulk training assignments, overdue tracking, CSV reports and an activity log. See [admin setup and validation](docs/admin-workspace.md). The 0.7.0 development APK adds a native training-centre workspace and independent Suraksha accounts. Public hosting remains unresolved.

## Run locally

From the repository root, after installing web dependencies:

```sh
node scripts/local-dev.mjs
```

The launcher preserves existing signing/account keys, applies local migrations and starts the learner portal at `http://localhost:5173/learn`, staff workspace at `http://localhost:5173`, and temporary development inbox at `http://127.0.0.1:5188`. No external email is sent. For the debug Android app over USB, use `adb reverse tcp:5173 tcp:5173`. Complete centre approval using the existing operator procedure; signup does not grant staff access.

Register the Android learner ID in Workers, create a private invitation in **Learners & observations**, then join and sync in Android **My training centre**. Finish and sync all four course stages before requesting certification. A different certifier reviews the evidence and can request clarification, reject, or approve. Approved credentials sync into the Android wallet and have a printable learner web record.

### Manual dashboard startup

```sh
cd apps/admin-web
npm ci
npx wrangler d1 migrations apply DB --local --config wrangler.local.json --persist-to .wrangler/state
npm run dev
```

Open the printed localhost address and create/sign in to an independent account. Follow the [operator and centre approval setup](docs/government-demo-0.9.0.md) before managing records. Complete training on Android, use **My record → Export records for trainer**, and import the JSON in the dashboard. Open a passed assessment, choose the expiry approved by the site training policy, request certification review, have a different certifier approve it, then scan the QR or open its downloaded text file in Android.

For room practice, use **My record → Export room practice journals** and the separate **Room practice** dashboard tab. These histories cannot issue certificates.

See [dashboard setup](apps/admin-web/README.md) for issuer configuration, tests and deployment details. Internet hosting is not yet available: the Sites registration request failed and discovery found no created Suraksha site.

## Validation

Run `bash scripts/build-android.sh` for grading unit tests and APK build. Android instrumented tests are in `apps/android/app/src/androidTest` and exercise an assessment across activity recreation, Hindi home rendering, signed credentials, critical failure, all five 3D equipment models, staged component recognition, assistance tracking and database migration. The web checks are `npm test`, `npx tsc --noEmit`, `npm run build`, and `node tests/integration.mjs` with the local server running. Integration tests create explicitly labelled demo records.

See the [complete requirements tracker](docs/requirements-tracker.md), [source-based reference comparison](docs/reference-benchmark.md) and [current implementation and remaining work](docs/build-status.md). Target architecture and delivery plans are in [solution blueprint](docs/solution-blueprint.md), [engineering specification](docs/engineering-specification.md), [delivery plan](docs/delivery-and-demo.md), and [design system](docs/design-system.md). Those plans include features not yet implemented.

## Before field release

Reviewed Santali text/audio, safety-content approval, broader physical AR testing, richer physical AR actions, field validation of practical assessment and sync, production identity/access validation and production distribution are still required. The supplied brief was truncated at “(3) Machinery”; domains 3–5 in the design documents are proposals.

See [learning research and six interaction designs](docs/ar-learning-evidence.md), [implemented spaced review](docs/spaced-review.md), and [staged component practice](docs/component-practice.md). The pilot does not claim measured learning gains.
