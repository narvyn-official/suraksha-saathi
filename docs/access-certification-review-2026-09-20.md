# SurakshaAr access and certification follow-up — 20 September 2026

Reviewed from `79cad92` after the 0.8.0 checks. Scope: identity, workspace ownership, team roles, revocation, assessment-to-credential flow, and native credential delivery. This is a focused product/access review, not a full security audit or certification-policy approval.

## Findings

| Priority | Finding | Status |
|---|---|---|
| High | Every independent sign-up implicitly owns an admin workspace. There is no platform approval, centre accreditation, or global account suspension workflow. | Confirmed current model; policy decision requested before changing existing access. |
| High | Both admins and trainers can issue/revoke pilot credentials through the shared configured issuer. There is no separately approved issuer role, approval queue, or separation between the person importing evidence and approving issuance. | Confirmed current model; needs explicit certification authority policy. |
| High | Issuance accepts a stored passed assessment by ID. It does not require the latest assessment or current curriculum version, assessor attestation, or verified learner identity. A later failed attempt does not prevent selecting an older pass. | Confirmed code path; a stricter issuance policy is proposed below. Existing tokens must not be silently rewritten. |
| Medium | Native admin QR/share output was a raw signed token, while the learner importer only dispatched prefixed credential text. A valid native-issued record could not enter the learner verification flow. | Fixed: standardized QR/share prefix; learner importer also accepts existing raw signed tokens and whitespace. All paths retain signature/issuer/scope validation. |
| Medium | Web refresh with revoked workspace access discarded the authenticated session and available workspace choices. Some child views could also retain stale workspace content. | Fixed: keep signed-in recovery controls, clear credential/assessment state, unmount protected views when no workspace is authorized, and show an explicit recovery notice. |
| Low | The role helper treated unknown role values as readable if called directly, although the current membership resolver filters them first. | Fixed: unknown roles fail closed; full role matrix unit-tested. |
| Product gap | Offline learner profiles are device-local identities, not authenticated user accounts. A shared-phone profile selector is not proof of identity or a learner login. | Documented limitation; do not label it identity assurance. |
| Product gap | A completion PDF is a device-generated receipt. Signed pilot credentials still say practical competence is not assessed; offline signature verification cannot establish current revocation. | Existing distinctions retained. No formal safety certification claim. |

## Current enforced matrix

| Action | Learner profile | Viewer | Trainer | Centre admin/owner |
|---|---|---|---|---|
| Offline lessons / local records | Yes, on device | Separate learner feature | Separate learner feature | Separate learner feature |
| Read centre workers, assessments, journals, credentials and audit | No account access from a profile alone | Yes | Yes | Yes |
| Import evidence / manage workers / assignments | No | No | Yes | Yes |
| Issue/revoke pilot credentials | No | No | Yes — policy gap | Yes — no external approval |
| Invite/manage team and centre settings | No | No | No | Yes |
| Change another workspace by guessing its ID | No | Denied | Denied | Denied unless independently authorized |
| Remove/demote implicit workspace owner | No | No | No | Owner protection enforced; no ownership-transfer workflow |

Roles are checked by the server against current membership on each request, rather than relying only on hidden controls. Invitation acceptance requires the one-time private code and matching signed-in email. Sign-up does not verify email ownership by itself; do not infer identity from the email string. The original workspace owner is an implicit admin and is not represented like other team members in the staff table, which is another clarity/operational limitation.

## Recommended target to confirm

1. New accounts begin without administrative or signing authority. Learners can use offline training; staff request/join an approved centre.
2. A platform operator approves centres and first administrators through an audited provisioning flow. Existing centres need an explicit migration list, not automatic accreditation or deletion.
3. Trainers manage learning records and submit certification requests. Approved certification administrators review and approve/reject; issuing and revoking remain separately authorized server capabilities. No client-supplied role or checkbox grants authority.
4. New issuance requires a named centre and issuer, registered learner reference, latest eligible assessment, reviewed curriculum, explicit approved validity and an immutable evidence snapshot. Practical/site assessment remains separate and must not be fabricated from app events.
5. Record requested/approved/rejected/issued/revoked states with actor, timestamp and reason. Show actual capabilities in both web and Android, including pending/rejected/suspended states and recovery.
6. Keep legacy signatures verifiable, but show missing governance/identity evidence. Publish an explicit policy for existing records and revocation; do not silently make old records accredited certificates.

The pending user choice is whether to require approval for centre administration as well, or retain self-service centres with approval restricted to certification. No production centre or account was approved, suspended, or revoked during this review.

## Verification

- Web: 26 unit tests; TypeScript, ESLint and production build pass.
- Existing admin integration: authentication boundary, header spoofing, private invitations, tenant isolation, all three roles, revoked access, workers, assignment evidence/cancellation and audits pass against the local service.
- Android: 157 JVM tests and build/lint pass (existing lint warnings remain). Eight targeted instrumentation tests pass: credential signature/expiry/import, worker-wallet isolation and native account workflow. The new test exercises learner import for prefixed and historical raw native tokens.
- Browser: synthetic viewer membership selected, revoked locally, refreshed, then switched back using the still-visible authorized workspace selector. Protected content is removed when no current workspace is authorized.
- Temporary local workspace fixture only; no real team member was changed, no external message sent, and no production database accessed.

Applied development-rulebook SEC-01/02/15 and FLOW-02/04/08: explicit role matrix, fail-closed authorization, tenant boundaries, state recovery and tests by entry path. Approval/identity controls above remain open rather than being represented as completed by passing regression tests.
