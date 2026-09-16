# Login and administration — 16 September 2026

The dashboard now has a dedicated login surface and a shared training-centre workspace. The Android APK remains version 0.6.0; this increment changes the web application only.

## Access

- `/` shows sign-in to anonymous visitors and the dashboard after authentication. `/login` provides account entry, and sign-out uses the platform-owned route.
- Sign-in uses the supported ChatGPT identity flow. The local preview intentionally uses the starter's development identity; it is not a password-based public login service. Password recovery and account authentication belong to the identity provider.
- Each signed-in user retains their personal workspace and can accept email-matched invitations to other centres. Accepted membership is bound to the stable authenticated user ID. A unique database index prevents duplicate membership identities in one centre.
- The workspace-selection cookie is HttpOnly and SameSite=Lax (Secure on HTTPS). It selects a workspace only: every API request checks current membership and role. Revocation takes effect on the next request; an old cookie cannot restore access.
- Admins manage staff and settings; trainers manage workers, assignments, imports and pilot credentials; viewers can read, verify and export. The owner always retains administration. No invitation email is automatically sent. Invitees must also satisfy the hosting platform's access policy.

## Workflows

1. Sign in, then open Settings to name the centre and site.
2. Under Team access, save an email and role. Share the dashboard address yourself. The recipient signs in with that email and selects the invited workspace.
3. Import Android assessments or room practice, or register a worker. An optional existing Android worker ID links a new registration to that phone profile. Without that ID a new register entry is standalone; import Android records before assigning device training. Existing saved identities are not rewritten.
4. Assign a module to up to 100 selected workers with a due time and optional instructions. Completion requires the latest imported assessment for the assigned curriculum version **started after** assignment to pass. Older passes and practice cannot complete it; a later failure removes completion status. This relies on the phone's reported clock, not trusted device attestation.
5. Filter assigned, overdue, completed and cancelled work. Export the filtered report as CSV. These statuses concern the centre's training plan, not verified legal compliance. Cancellation preserves the record and reason.
6. Activity records staff acceptance, settings/profile/access changes, assignment creation/cancellation, imports and credential issuance/revocation. It starts with this increment and does not reconstruct older events. The application exposes no audit edit/delete endpoint.

Existing assessment review, curriculum, worker analytics, room-practice snapshots, signed QR download/verification and revocation remain available. Screen/camera practice remains separate from certification.

## Installation and bounds

Apply D1 migrations `0003_admin_workspace.sql`, `0004_admin_lookup_indexes.sql` and `0005_assignment_curriculum_version.sql` through the standard local migration command in the dashboard README. Existing personal records keep their owner keys. No private issuer key changes or Android migrations are needed.

Management displays the latest 1,000 workers/assignments, 200 staff members and 200 audit entries, with total record counts where provided. Existing assessment/practice views retain their disclosed bounds. Assignments are manually transferred planning records: automatic phone delivery, notifications, organization SSO, email/password accounts and practical assessor sign-off are not implemented.

## Verification

- New API integration checks: local sign-in/out contracts, anonymous rejection, forged identity headers, invited workspace acceptance, cookie flags, guessed/cross-workspace access, all role write restrictions, revocation, personal-workspace recovery, worker registration/editing/Android-ID matching, assignment evidence/overdue/cancellation, audit attribution and cross-origin writes.
- Existing assessment/credential and room-journal API integrations passed after authorization changes.
- All 20 existing web unit tests passed; TypeScript checking passed.
- No browser visual or interaction test was performed. Login and authenticated dashboard routes returned HTTP 200; the production build passed with the existing large-client-chunk warning.

Hosting remains unresolved: the original Sites registration returned an uncertain transport failure, and fresh complete discovery found no Suraksha site. The workflow forbids duplicate creation after that uncertain result. No live login URL or production identity-provider round trip is claimed.
