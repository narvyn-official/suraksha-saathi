# SurakshaAr local learning journey release

Scope: P0–P2 with local hosting. Preserve offline training, the current curriculum and v1/v2 room journals. Preserve unrelated hospital concept and solution blueprint work.

Design: familiar guided learning steps and calm staff workspace, using existing native controls and web primitives. Compare a dashboard-first approach with a course-first approach: choose course-first for learners and retain the operational dashboard for staff. No new design dependencies or third-party assets. Rulebook FLOW, AUTH, SES, SEC, STA, NAT, ENG and ART apply.

Acceptance checks:
- Learners can practise without an account; staff roles cannot be self-selected.
- Local server has a reproducible launcher; mail stays in a loopback development inbox.
- Safe login return paths; shared-device session choice; MFA challenge and enrolment.
- Enrolment binds a specific learner and centre using a single-use invitation; another learner cannot read/sync/request that learner's records.
- Course stages represent recorded evidence, preserve practice access, and resume safely.
- Extended procedural scenarios cover all five modules, save progress and expose decisions/feedback. Existing room controls remain available.
- Retried sync is idempotent; stale/conflicting data does not overwrite evidence. Credentials are downloaded only into their learner's wallet.
- Certification exposes a readiness checklist, correction cycle, timeline, rubric, independent approval and a signed record with accurate scope.
- Instructor observations are separately attributed and never silently relabel simulation tokens as statutory qualifications.

## Implemented scope

| Priority | Delivered |
| --- | --- |
| P0 | Distinct learner/staff entry, safe login return path, shared-device session default, local password reset and email verification, optional authenticator MFA with recovery codes, session revocation, invitation-based learner enrolment, visible course/certification status. |
| P1 | Read/listen → worked example → guided rehearsal → independent scenario → knowledge assessment → independent review. Course prerequisites are checked on the server. Structured review rubric, needs-information/resubmission, attributed timeline, private printable certificate, minimal public status lookup and revocation. |
| P2 | 55 procedural steps: fire 16, gas 13, machinery 10, PPE 8, emergency 8. EN/HI choices, scene changes, virtual target holds, action debriefs, interruption recovery, instructor observations and evidence packets, encrypted durable retry batches, changed-record uploads and credential downloads. Existing fire/gas room gestures remain available. |

Scenario additions are original draft content in `content/scenario-additions-v2.json`; run `python3 scripts/generate-scenarios.py` after editing to regenerate the native definitions and server replay catalogue. Version 1 journals retain their original sequence. New requests use workflow version 2; existing pending version 1 requests retain their original assessment-only policy and existing issued tokens remain unchanged.

## Local operation

1. Install web dependencies (`cd apps/admin-web && npm ci`) and build with `bash scripts/build-android.sh lintDebug` from the repository root.
2. Run `node scripts/local-dev.mjs` from the repository root. Keep that process running.
3. Learners: <http://localhost:5173/learn>. Staff: <http://localhost:5173>. Development inbox: <http://127.0.0.1:5188>.
4. On a USB-debugging phone, use `adb reverse tcp:5173 tcp:5173`, then install `artifacts/surakshaar-0.10.0-debug.apk`. The debug app defaults to localhost; release builds still require an explicit HTTPS training server.
5. Approve the training centre through the established operator workflow in `government-demo-0.9.0.md`. Register the phone's learner ID, create its invitation, and enrol using the invited account. The launcher does not auto-approve centres or create privileged users.
6. Complete and sync the four stages. Request review, use a distinct certifier, then sync the signed credential. Use the learner portal to print/save PDF. Offline wallets show the time of the last server status check; current revocation requires a connection.

Account secrets remain in ignored `.dev.vars`; Android session cookies and queued batches use Android Keystore encryption. A shared-device login is held in process memory. Local mail is memory-only and loopback-only; anyone using the host can open this development inbox, so it is for local testing. The inbox never connects to an external mail provider.

## Scope and release limits

- This is a local pilot, with no new public deployment or accreditation claim. Simulation tokens still say practical competence is not assessed, even when a separately attributed instructor observation exists.
- No current physical phone was available for the new release. Emulator tests can verify scene rendering and workflows, but cannot validate floor tracking, real-world gestures, camera alignment or physical usability. Previous Samsung evidence belongs to earlier releases.
- Scenario content and Hindi wording require competent site/safety and native-language review before field use. Generic OSHA guidance informed draft awareness content; it is not Indian statutory approval. See [energy isolation guidance](https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.147) and existing curriculum references.
- Sync retries run on foreground/resume or manual sync. This does not claim continuous background upload while Android has terminated the process. Evidence stays locally saved until connection is restored. The app uploads decision records, not camera frames.
- Spatial interactions measure virtual targeting. Screen and text alternatives are retained and labelled; they do not establish real equipment-handling skill. No hand tracking or calibrated gas measurement is claimed.
- MFA is optional in this local pilot; broader production identity policy, operator MFA enforcement, retention/backups, deployment hardening, reviewed Santali content and a physical-device matrix remain field-release work.

## Verification

Results below are specific to this local release. The test accounts and records are synthetic.


- Web: 31 unit tests passed; TypeScript, ESLint and production build passed. The migration test preserves prior pending/approved/rejected requests and signed-credential references. Existing Vite native-loader and Node deprecation warnings remain non-blocking.
- API integration: enrolment identity/tenant isolation, one-use invitation, replay validation, idempotent/stale sync, missing-course rejection, learner and trainer clarification cycles, evidence packet, approval rubric, signed wallet, public privacy and revocation passed.
- Account integration: actual authenticator enrolment, password-only session denial, invalid factors and one-use recovery codes passed. Local mail integration exercised delivery, one-use password reset, old-session revocation, new-password sign-in and verification. Reliability regression passed concurrent imports/issuance and reset replay/expiry handling.
- Android: 160 JVM tests passed; debug APK build and lint completed with zero lint errors. Native transport tests verify an encrypted failed-upload queue, exact retry, changed-record uploads and denial after switching accounts. Demonstrations do not create assessment credit.
- Browser: inspected the learner map at 390px and 1280px, submitted a synthetic review request, opened its staff evidence packet, checked the private certificate layout and public status lookup, then signed out and removed this run's visual-review fixtures. Browser-native PDF output was not separately exported as an artifact.

Reproduce web tests from `apps/admin-web` with `npm run check`, then `npx tsx tests/journey.integration.ts`, `npx tsx tests/mfa.integration.ts`, `npx tsx tests/local-recovery.integration.ts`, and `npx tsx tests/reliability.integration.ts` against the running local stack. The suites honor real rate limits. Run them sequentially.

Build Android tests with `bash scripts/build-android.sh lintDebug assembleDebugAndroidTest`; use `python3 scripts/test-android-matrix.py --serial emulator-5554 --out artifacts/p2-final-android` for the emulator-only permission/font/thermal fixtures. A raw full instrumentation run without those fixtures produces expected environment failures.

### Final emulator result

98 unique emulator checks passed across the matrix and a targeted rerun: 88 ordinary, 2 placement, 2 workspace, 4 large-text and 2 thermal checks. The matrix initially passed 87/88 ordinary checks; the placement-help test had left a fresh camera permission prompt open. Its fixture now explicitly declines that prompt, and the targeted rerun passed. All five expanded scenarios completed through the UI with rendered equipment and saved decision histories.

Evidence: `artifacts/p2-final-android/*.log` and `artifacts/p2-placement-help-rerun/ordinary.log`. The AR reference-database check was not run because this emulator has no Google Play Services for AR. This is not a physical tracking result.

APK: `artifacts/surakshaar-0.10.0-debug.apk` (debug-signed local pilot). SHA-256: `095c349641992185ddaafba51d66903b937c2a3898117be75aad931153b68956`. Matching checksum file: `artifacts/SHA256SUMS-0.10.0.txt`.

Local development is left running on port 5173, with the development inbox on 5188. Account hosting remains local. The source and tested APK are published as the 0.10.0 pilot prerelease; see [release scope and checks](release-0.10.0.md). Unrelated hospital-concept, development-rulebook and solution-blueprint changes were preserved.
