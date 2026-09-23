# SurakshaAr 0.10.0 pilot release

23 September 2026. This release connects offline learning to a learner portal and independent certification review, and expands ordered practice to 55 steps across five domains. Account hosting remains local. The APK is debug-signed, version code 23, with Android 10 as its minimum supported version; camera AR also requires an ARCore-supported device and services.

## Changes

- Separate learner and staff entry, invitation-based enrolment, shared-device session defaults, local recovery mail, optional authenticator MFA and session revocation.
- A visible learning journey with server-checked lesson, guided procedure, latest independent procedure and current passing knowledge assessment prerequisites for new requests.
- Reviewer checklists, clarification and resubmission, attributed instructor observations, private printable credentials and limited public status lookup.
- Fire 16, gas 13, machinery 10, PPE 8 and emergency 8 procedure steps, including virtual interactions, feedback, debriefs and interruption recovery.
- An encrypted durable upload queue, account/profile binding, changed-record uploads and signed credential downloads. Retries run in foreground/resume or manual sync.

See [implementation details and reproducible checks](p0-p2-local-implementation.md) and [local operation](../README.md#run-locally). Apply migrations through `0011_course_review_policy.sql`. Preserve existing issuer keys, trust configuration and learning histories. Do not downgrade application code alone across the changed certification policy; restore a matching tested application/database backup in isolation if recovery is needed.

## Release acceptance evidence

Scope: source publication and a local pilot APK, not public-service deployment. Review: Codex local verification, 23 September 2026. Field-readiness decisions remain with the project and intended institution.

| Checklist area | Status | Evidence and remaining limits |
|---|---|---|
| Product and role workflows | Pass for tested local scope | Enrolment, tenant/profile isolation, course prerequisites, correction/review, wallet and revocation integration checks passed. |
| Screens, transitions, overlays and feedback | Partial | Native interruption/recovery checks and browser journey checks passed. Full assistive-technology coverage remains untested. |
| Visual, responsive and accessibility checks | Partial | Browser 390px/1280px inspection and native large-text fixtures passed. No full accessibility conformity claim. |
| Accounts and security | Pass for tested cases | MFA, one-use recovery, session revocation, role denials, immutable evidence and concurrency checks passed. MFA remains optional. |
| Data and privacy | Partial | Public status omits learner name; scoped access tested. Signed payloads are readable. Retention/deletion policy and restore drills remain field work. |
| Performance and platforms | Partial | Changed-record uploads and emulator thermal fixtures checked. No field performance benchmark or current physical AR result. |
| Engineering and supply chain | Pass for local checks | 31 web unit tests plus type/lint/build; 160 Android JVM tests and debug build/lint; dependency audit on release preparation reported zero vulnerabilities. |
| Emulator validation | Pass for stated matrix | 98 unique checks passed across the matrix and one targeted rerun. AR reference-database check excluded because emulator lacks Google Play Services for AR. |
| Operations and deployment | Not field ready | Local server/inbox verified. Public HTTPS account hosting, institutional recovery, monitoring and backup restore are not provisioned here. |
| Commerce, SEO, AI experiments and new design assets | N/A | No commerce, public marketing-site change, AI inference or new third-party design asset in this release. |

The test counts describe the previously completed local implementation checks. GitHub Actions independently validates the pushed source. Detailed fixture conditions and the targeted rerun are recorded in the implementation report. Tests use synthetic records and do not measure learning gain or safety outcomes.

## Distribution boundaries

The APK contains no demonstration account passwords, database copies or issuer private key. Private login sheets, local demo records, runtime configuration and unrelated project work are excluded from this release. Use `adb reverse tcp:5173 tcp:5173` for the debug app's local account connection while the development server is running.

These are pilot simulation credentials, not government-accredited qualifications, verified identity, practical competence or authorization to work. Safety content and Hindi wording require competent review. Physical AR must be tested on the target phone; earlier Samsung results belong to earlier releases.

APK SHA-256: `095c349641992185ddaafba51d66903b937c2a3898117be75aad931153b68956`.
