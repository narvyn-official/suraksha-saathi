# Suraksha Saathi

A working **v0.2 pilot** for Android safety learning and trainer review, built for Jharkhand’s industrial workforce with a fixed light interface.

[Download the APK and emulator demo](https://github.com/narvyn-official/suraksha-saathi/releases/tag/v0.2.0-pilot).

## What you can use now

- Android app with English and Hindi lessons, guided practice and assessments for fire response, gas/confined spaces and machinery/isolation (24 decisions).
- Offline SQLite progress, interrupted-attempt recovery, critical-error assessment gates, installed offline voice support and PDF completion receipts.
- Original inspectable illustrations, offline rotatable 3D equipment and ARCore camera mode with tracked equipment and decision cards on supported phones; screen practice on other Android 10+ phones.
- Trainer dashboard with a worker directory, sector/status filters, latest-assessment charts, practice follow-up, CSV export, persistent records, answer-by-answer review, signed pilot credentials, QR downloads, signature checks and revocation.
- Android credential wallet with pinned-issuer signature verification offline.

**Pilot scope:** these are simulation learning records, not statutory safety certificates, identity verification, practical competence or permission to work. Santali is visibly pending native review. Actual AR tracking needs physical-phone validation.

## Build Android

Install Android SDK platform/build tools 36 and a compatible JDK, then:

```sh
bash scripts/build-android.sh
```

The debug-signed APK is copied to `artifacts/suraksha-saathi-0.2.0-debug.apk`. Android Studio can open `apps/android`. The repository includes the Gradle wrapper. For a fresh clone, generate the pilot issuer before building if you want your own trainer-issued credentials:

```sh
node scripts/create-pilot-issuer.mjs
```

The command creates an ignored private runtime key and matching public trust files. It refuses to overwrite an existing private key. Never publish `.dev.vars` or signing keystores.

## Run the dashboard

```sh
cd apps/admin-web
npm ci
npx wrangler d1 migrations apply DB --local --config wrangler.local.json --persist-to .wrangler/state
npm run dev
```

Open the printed localhost address and select **Sign in** for the local development identity. Complete training on Android, use **My record → Export records for trainer**, and import the JSON in the dashboard. Open a passed assessment, issue its pilot credential, then scan the QR or open its downloaded text file in Android.

See [dashboard setup](apps/admin-web/README.md) for issuer configuration, tests and deployment details. Internet hosting is not yet available: the Sites registration request failed and discovery found no created Suraksha site.

## Validation

Run `bash scripts/build-android.sh` for grading unit tests and APK build. Android instrumented tests are in `apps/android/app/src/androidTest` and exercise an assessment across activity recreation, Hindi home rendering, signed credentials, critical failure and the three 3D equipment models. The web checks are `npm test`, `npx tsc --noEmit`, `npm run build`, and `node tests/integration.mjs` with the local server running. Integration tests create explicitly labelled demo records.

See the [source-based reference comparison](docs/reference-benchmark.md) and [current implementation and remaining work](docs/build-status.md). Target architecture and delivery plans are in [solution blueprint](docs/solution-blueprint.md), [engineering specification](docs/engineering-specification.md), [delivery plan](docs/delivery-and-demo.md), and [design system](docs/design-system.md). Those plans include features not yet implemented.

## Before field release

Reviewed Santali text/audio, safety-content approval, real AR phone testing, the remaining two domains, richer physical AR actions, practical assessment, secure automatic sync, organisation roles and production distribution are still required. The supplied brief was truncated at “(3) Machinery”; domains 3–5 in the design documents are proposals.
