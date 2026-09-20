# SurakshaAr 0.9.0 — release evidence

20 September 2026. Controlled government demonstration candidate. Android version code 22; web package 0.9.0. [Setup, access matrix, migration, rollback and demonstration script](government-demo-0.9.0.md).

## Implemented

- Centre applications and independent operator approval, rejection, suspension and restoration. Signup grants no administration or signing authority.
- Admin, trainer, viewer and certifier capabilities enforced by current server membership and centre state. Sensitive decisions require recent authentication.
- Certification requests with evidence notes and expiry, independent review, rejection reasons, atomic signing and audited revocation. Latest assessment/current curriculum gates prevent an earlier pass from hiding a later failure. Conflicting request retries cannot silently replace another note or requester; rejected requests cannot masquerade as new submissions.
- Signed centre/request/reviewer/evidence provenance, legacy metadata distinction, 768-pixel QR download and preserved Android trust/prefix compatibility.
- Native pending-centre and certifier screens, learner-specific reading bookmarks and next-step learning guidance.
- Deferred charts and QR assets, indexed latest-evidence queries, clock refresh after record/verification responses and an accessible approval navigation entry.

## Checks completed

| Check | Result / evidence |
|---|---|
| Web unit tests | 26 passed |
| TypeScript, ESLint, production build | Passed |
| Dependency audit | `npm audit --audit-level=high`: 0 vulnerabilities reported |
| Authentication integration | Signup/signin, invitation possession/single use, permissions, CSRF, password change, logout and session revocation passed |
| Administration integration | Tenant isolation, worker/assignment lifecycle, latest-evidence assignment status, membership revocation and audit passed |
| Assessment/credential integration | All five domains, archived curriculum imports, replay, invalid/conflicting data, independent signing, expiry, tamper detection and revocation passed |
| Room-journal integration | Separate evidence schema, immutable snapshot history, legacy compatibility, bounds and atomic conflicts passed |
| Reliability integration | Concurrent import/request retries, one signing audit, password policy, synthetic recovery, atomic reset/replay/expiry and session revocation passed; no external email sent |
| Governance integration | Default-denied signup, approval/rejection/resubmission, operator self-review denial, independent certifier/self-request denial, concurrent signing, latest-failure gates, suspension/restoration, revoked role, stale session and scoped evidence lookup passed |
| Operator bootstrap | Missing local account rejected; adding/removing the synthetic operator preserves other configuration |
| D1 migration | 0008 applied successfully to the local database; existing records retained |
| Query plan | Latest-assessment lookup uses covering index `idx_attempts_latest_assessment`; no temporary sort in the inspected plan |
| Deferred build assets | Manifest marks Insights and QR browser modules as dynamic entries. Approximate gzip sizes: 104 KB and 9 KB. These are bundle measurements, not measured field latency gains |
| Android JVM/build/lint | 157 tests passed; debug APK and test APK build passed; lint has 0 errors and 36 existing warnings |
| Emulator instrumentation | 95 tests in the explicit font/permission/thermal matrix plus 1 new signed-governance QR test passed; 96 total |
| Browser | Centre-access recovery, approved-workspace selection, separate certifier queue, answer dialog/Escape/focus return, recent-sign-in failure and fresh-sign-in approval, QR/provenance display and 390/320 px layouts inspected |
| Physical Samsung SM-S921B / Android 16 | 0.9.0 installed over 0.7.1 without clearing app data. Three targeted tests passed: reading position across fresh launches, web-signed governance QR/native signature verification, AR reference database compilation |

The emulator matrix requires explicit environment fixtures. An initial unconfigured all-class invocation was stopped after expected font/permission-fixture errors; the documented matrix then passed. The emulator lacks Google Play Services for AR, so reference database compilation was checked on the connected phone instead. Reference-asset compilation and generated QR round-trips do not prove physical image tracking or a camera scan.

## Development-rulebook acceptance

Applied the personal rulebook proportionately to the existing Mineral Light UI and fixed native pages: FLOW/SEC for least privilege and separate actors; SES for recent sensitive authentication; STA/REC for pending, rejected, suspended, revoked and recovery states; MIC/ENG for idempotence and concurrent decisions; A11Y/RSP for labels, focus, keyboard dismissal and narrow layouts; PERF for deferred assets and indexed queries; DSO/DONE for checks, migration, secret handling and release evidence. No new third-party visual assets or effects were introduced. Full screen-reader review, a formal accessibility audit and production load tests remain unperformed.

## Limits and release decision

Ready for a configured, supervised demonstration with synthetic data. Not a government-accredited or production field release. Approval is pilot workspace approval; centre admins appoint their certifiers. Imported phone events are validated but are not hardware-attested evidence or verified identity. Practical competence remains separately unassessed. Offline verification cannot establish current revocation.

Production hosting, named operator identity, MFA/SSO, real recovery delivery, backup restoration, monitoring, qualified safety-content/practical-assessment review, Santali review and broader device/learning-effectiveness studies remain release gates. Review-list limits and migration impacts are explicit in the setup guide. No production centre was approved, no real invitation/recovery email was sent, and no server deployment was performed. Existing unrelated repository work was preserved.

Live physical AR: after the user opened the camera view, the Samsung reported fresh TRACKING frames, a floor hit at about 1.49 m and one accepted real anchor. Seven observed five-second windows reported 132–149 tracked frames (about 26–30 fps), max AR update time 10–16 ms and thermal status 2. These are short diagnostic samples, not a long-duration benchmark or a claim of cool operation. The USB connection then dropped. Three-station stability, a full current-build mission and actual camera QR scanning were not confirmed. No full physical AR pass is claimed.

Final GitHub CI status and downloadable artifact checksums accompany the delivered report.
