# Suraksha Saathi trainer dashboard

Private trainer workspace for importing Android training exports, reviewing decisions, issuing pilot simulation credentials, and checking signatures and revocations.

## Local run

From the repository root, run `node scripts/create-pilot-issuer.mjs` **only on first setup**. It generates a local signing key in ignored `apps/admin-web/.dev.vars` and embeds the matching public trust anchor into both apps. Existing issuer settings are never overwritten. Rebuilding Android is necessary after an intentional issuer change. Never commit or distribute the private key.

Then:

```sh
cd apps/admin-web
npm ci
npx wrangler d1 migrations apply DB --local --config wrangler.local.json --persist-to .wrangler/state
npm run dev
```

Open the printed localhost address and choose **Sign in**. The starter's development sign-in uses a local-only identity; it is not a production login. Production routes require the Sites-injected identity and are scoped by owner ID. There is no bypass based on a user-supplied owner field.

Import the Android JSON export, select **View**, and issue a pilot credential for a passed assessment. The QR is signed with ES256. The Android app pins the public issuer key. Offline verification confirms signature and scope, **not** revocation. The dashboard checks its stored current status. Records do not prove the learner's identity or practical competence.

## Checks

```sh
npm test
npx tsc --noEmit
npm run build
node tests/integration.mjs
```

Integration checks require the localhost development server and migrations. They create clearly named demo records in the local database. They cover unauthenticated rejection, imports, score replay, idempotency, conflict rejection, invalid answers, issuance, tampering, and revocation.

D1 migrations are in `drizzle/`. The private issuer runtime value is `ISSUER_PRIVATE_JWK`; configure it in the deployment platform's secret storage. The public file is `lib/trusted-issuer.json`. Never put private values in `.openai/hosting.json`, source control or browser code.

## Deployment state

Sites registration failed during this build. Discovery confirmed no Suraksha site was available, and registration was not duplicated. The dashboard is usable locally; no public verification endpoint or automatic phone sync is claimed. Resume hosting after the original registration issue is resolved. Production build and migrations are prepared.

## Pilot limitations

The dashboard displays up to the latest 500 attempts and credentials per owner. Imports accept 1–100 completed attempts and files up to 1 MB. There are no bulk worker assignments, practical-assessment signing, automatic sync, organisation roles, official certification, key rotation UI or public verification service yet. Imported event histories are consistency checked, not hardware attested.

## Evidence analytics

Overview uses each worker’s latest assessment per module. Practice does not count toward assessment coverage. Workers supports name/ID search, sector and status filters, history review and CSV export. Workers are created or updated by validated Android imports. The coverage notice discloses when the latest-500 limit truncates evidence. Curriculum versions 0.1.0, 0.2.0 and 0.3.0 are supported explicitly; unknown versions are rejected.
