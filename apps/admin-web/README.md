# SurakshaAr trainer dashboard

Private trainer workspace for importing Android training exports, reviewing decisions, issuing pilot simulation credentials, and checking signatures and revocations.

## Login and admin workspace

See [login, roles, worker management, assignments and audit setup](../../docs/admin-workspace.md). The dashboard now opens on a dedicated sign-in screen, with shared workspaces and Admin / Trainer / Viewer permissions checked server-side. Team invitations are saved in the workspace; no email is sent automatically. Accounts use independent Suraksha email/password authentication, shared with the native Android admin workspace.

Apply migrations through `0007_assessment_import_guard.sql` before running this version. In addition to the existing integration checks, run `npx tsx tests/admin.integration.ts` locally. Its temporary workspace fixtures are removed after the run.

## Local run

From the repository root, run `node scripts/create-pilot-issuer.mjs` **only on first setup**. It generates a local signing key in ignored `apps/admin-web/.dev.vars` and embeds the matching public trust anchor into both apps. Existing issuer settings are never overwritten. Rebuilding Android is necessary after an intentional issuer change. Never commit or distribute the private key.

Run `node scripts/create-app-auth.mjs` from the repository root to configure the independent local account service without printing secrets. Then:

```sh
cd apps/admin-web
npm ci
npx wrangler d1 migrations apply DB --local --config wrangler.local.json --persist-to .wrangler/state
npm run dev
```

Open the printed localhost address and create a Suraksha account. Email/password accounts are independent of ChatGPT. Every API checks the account session and current workspace role; a supplied owner ID cannot grant access. Production requires BETTER_AUTH_URL and BETTER_AUTH_SECRET in backend configuration. See the account recovery and ownership-migration limits in the admin setup guide.

Import the Android JSON export, select **View**, and choose a policy-approved expiry and issue a pilot credential for a passed assessment. Renewal requires a new passed assessment; existing dates cannot be extended. Legacy tokens remain signature-verifiable but display “No expiry recorded” and are excluded from active counts. The QR is signed with ES256. The Android app pins the public issuer key. Offline verification confirms signature and scope, **not** revocation. The dashboard checks its stored current status. Records do not prove the learner's identity or practical competence.

## Checks

```sh
npm run check
npm audit --audit-level=high
node tests/integration.mjs
```

Integration checks require the localhost development server and migrations. They create clearly named demo records in the local database. They cover all five domains, archived curriculum compatibility, false-version rejection, the emergency critical-error gate, unauthenticated rejection, imports, score replay, idempotency, conflict rejection, invalid answers, signed issuance/verification, tampering and revocation. An active emergency pilot credential is saved to ignored `artifacts/demo-emergency-credential.txt` for native verifier checks. It contains a signed demo token, not the private issuer key.

D1 migrations are in `drizzle/`. The private issuer runtime value is `ISSUER_PRIVATE_JWK`; configure it in the deployment platform's secret storage. The public file is `lib/trusted-issuer.json`. Never put private values in `.openai/hosting.json`, source control or browser code.

## Deployment state

Sites registration failed during this build. Discovery confirmed no Suraksha site was available, and registration was not duplicated. The dashboard is usable locally; no public verification endpoint or background phone sync is claimed. Resume hosting after the original registration issue is resolved. Production build and migrations are prepared.

## Pilot limitations

The dashboard displays up to the latest 500 attempts and credentials per owner. Imports accept 1–100 completed attempts and files up to 1 MB. Bulk assignments and workspace roles are implemented. Practical-assessment signing, automatic phone delivery/sync, official certification, key rotation UI and public verification hosting remain unavailable. Imported event histories are consistency checked, not hardware attested.

## Evidence analytics

Overview uses each worker’s latest assessment per module. Practice does not count toward assessment coverage. Workers supports name/ID search, sector and status filters, history review and CSV export. Workers are created or updated by validated Android imports. The coverage notice discloses when the latest-500 limit truncates evidence. Current curriculum 0.4.0 covers Fire, Gas / confined space, Machinery, PPE and Emergency response. All five latest module assessments must pass for complete coverage. Versions 0.1.0, 0.2.0 and 0.3.0 replay against their preserved archives; unknown versions and modules attributed to versions that did not contain them are rejected.

## Room-practice journals (0.6.0)

On Android use **My record → Export room practice journals**, then use the dashboard's **Room practice** tab. This is a separate export from assessment records. Apply migration `0002_room_journals.sql` through the normal D1 migration command before use.

The authenticated `/api/room-journals` endpoint supports v1/v2 histories, including incomplete attempts and recorded assistance. Imports replay supported actions, preserve immutable snapshots and accept identical reuploads idempotently. Conflicting histories are rejected atomically. Records are owner-scoped and never qualify for credential issuance. The phone exports up to 100 latest journals, reports omissions and bounds the file to 950 KB; the API limits stored history per owner to 2,000 attempts and 10,000 snapshots. These are locally reported simulation events, not hardware-attested performance.

Run `npx tsx tests/room-journals.integration.ts` against the local development server for the room API checks. Production hosting remains unresolved; native upload and file transfer both require the account service.

## SurakshaAr 0.8.0 recovery setup

New accounts and changed/reset passwords require 15–128 characters. Existing passwords continue to work until changed. The web and native Android account flows share the same account service. Web forms support password reveal and session-only sign-in by default for shared devices.

Configure `BETTER_AUTH_URL` as the canonical HTTPS account origin, plus backend secrets `RECOVERY_MAIL_URL` (an operator-owned HTTPS delivery endpoint) and `RECOVERY_MAIL_KEY` (at least 32 characters). The gateway receives `POST` JSON `{to, subject, text}` with `Authorization: Bearer <key>` and must return 2xx only after accepting delivery. Redirects and delivery failures are rejected. Provision provider credentials inside the gateway’s approved secret store, never in this repository or the browser. This change does not provision a mail provider or send real email.

Reset links expire after 15 minutes and carry their token in the URL fragment. A successful reset consumes the token, invalidates other outstanding links and revokes the user’s sessions. Known and unknown emails receive the same response. If delivery is not configured, recovery fails closed with a service-unavailable response. Production delivery, spam controls and domain authentication require operator validation before release.

For isolated local QA, the account origin and gateway may both use loopback HTTP. Set `RECOVERY_MAIL_URL=http://127.0.0.1:5188/send` and a disposable random key in ignored `.dev.vars`, then run `node tests/local-mail-server.mjs` alongside `npm run dev` and `npx tsx tests/reliability.integration.ts`. The stub accepts only `@example.test` recipients, stores messages in memory and never sends external email. Remove the temporary recovery settings after testing. Integration suites share rate limits: run them sequentially, with their retry handling enabled.

`npm start` serves the built worker on port 5173, matching local account configuration; `PORT=5174 npm start` changes the preview port. The launcher explicitly reads the ignored project `.dev.vars` without copying secrets into `dist`. Stop the development server first. Apply migration 0007 before running the new API; retain backups before any production migration. Database triggers prevent conflicting assessment imports even under concurrent requests; credential uniqueness and audit changes are tested against local D1.

The GitHub validation workflow checks the web build/types/lint/unit tests and Android build/lint/JVM tests. Full local account/room/reliability integration and the emulator fixture matrix are additional checks; they require configured local services and are not silently represented as CI coverage.
