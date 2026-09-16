# Native training centre and independent accounts — 0.7.0

Suraksha Saathi now includes a native Android training-centre workspace. Open the profile menu → **Admin dashboard**, or Help → **Admin dashboard**. Learning remains available offline without an account. The web dashboard is a companion to the same account service.

## Accounts and access

Suraksha email/password accounts replace ChatGPT authentication. Better Auth handles password hashing, server-side sessions, sign-out and password changes. Passwords require 12–128 characters. Sessions expire after seven days; changing a password revokes other sessions. Authentication requests are rate limited in the database. Neither ChatGPT identity headers nor the starter's development cookie authorizes a training API.

The Android client stores session cookies encrypted with Android Keystore and never stores passwords. It accepts HTTPS origins; debug builds additionally allow only localhost/127.0.0.1 for emulator testing. Redirects cannot send account data to a different service. Changing the configured server clears the local session. The release manifest does not permit cleartext traffic.

Each account owns a centre. Admin / Trainer / Viewer permissions and active membership are checked on every server request. A centre selector cookie does not grant access. Team invitations generate a private, one-time code valid for seven days, displayed only when created. The invited email and code are both required to join; unverified email alone grants no membership. Only a hash of the invitation secret is stored. No email is automatically sent.

Email verification and forgotten-password recovery are not configured. Staff must keep their password; the password-change flow requires the current password. Historical records retain their existing owner IDs. Existing ChatGPT-linked records are not automatically reassigned to new accounts: a separately reviewed owner migration is required.

## In-app workflows

- Native login/signup, session restoration, sign-out, password changes and centre switching/joining.
- Overview, worker registration/editing with optional Android profile IDs, batch training assignments, cancellation and CSV text report sharing.
- Assessment review, signed QR issuance with an explicitly selected expiry, sharing, scanning, verification and revocation.
- Room-practice histories, curriculum and activity log; admin-only staff access and centre settings.
- Manual authenticated upload of the current local worker's completed assessment records and room-practice journals, after the destination centre is shown.

The native home uses fixed navigation and a selected module instead of a stacked catalogue. Lessons advance one at a time. Record collections use dedicated lists. Forms can scroll for the keyboard or enlarged text; content is not clipped to imitate a fixed screenshot.

Assignments complete only when the latest imported assessment of the assigned curriculum version, started after the assignment, passes. Older passes, practice and later failed assessments cannot be hidden. The phone's reported clock is not device attestation. Practical competence and permission to work remain separately assessed.

The browser companion also provides analytical filtering and downloadable CSV files. Android exposes the core workflows natively and shares assignment reports through the system share sheet. Automatic assignment delivery, background synchronization, notifications and practical assessor sign-off remain outside this pilot.

## Local setup

From the repository root:

```sh
node scripts/create-app-auth.mjs
# First setup only if an issuer has not already been configured:
node scripts/create-pilot-issuer.mjs
cd apps/admin-web
npm ci
npx wrangler d1 migrations apply DB --local --config wrangler.local.json --persist-to .wrangler/state
npm run dev
```

The auth setup script preserves existing values and appends a generated secret and local origin to ignored `.dev.vars`; it does not print secrets. Apply migration `0006_independent_accounts.sql` along with all preceding migrations.

For emulator testing, use `adb reverse tcp:5173 tcp:5173`; in native **Connection settings**, enter `http://localhost:5173`. A physical phone needs a reachable HTTPS account server for the admin workspace. Offline learning is independent of that server.

Production runtime configuration requires `BETTER_AUTH_URL` (the exact HTTPS origin), `BETTER_AUTH_SECRET` (at least 32 random characters), the existing `ISSUER_PRIVATE_JWK`, and the D1 binding `DB`. Store private values only in backend secret storage. Never include them in Git, the APK or hosting metadata.

## Bounds and verification

Management returns up to 1,000 workers/assignments and 200 staff/audit entries. Assessment and credential views return up to 500 records; room history retains its existing disclosed bounds. Native sync uses the selected local learner; it does not authenticate that learner's real-world identity.

Automated checks cover independent signup/signin, rejection of provider-cookie spoofing, possession-based invitation acceptance, one-time use, roles, revocation, cross-origin requests, password changes and revoked-session replay. Existing imports, grading, credentials and journal integrations continue to use real account sessions. Native emulator checks cover login, registration, encrypted cookie storage, session recreation, worker creation, sign-out and both-language learner navigation/viewport fit.

Public backend hosting remains unresolved: the original Sites registration had an uncertain transport outcome and complete discovery found no Suraksha site. No live public account endpoint is claimed. Native admin was verified against the local service through emulator port forwarding; physical-phone AR remains unverified.
