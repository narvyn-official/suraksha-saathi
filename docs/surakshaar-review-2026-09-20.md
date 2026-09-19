# SurakshaAr review — 20 September 2026

Baseline: `8962cbf`. Scope: existing Android learner/AR app and trainer dashboard, rename, corrective implementation and Git push. Preserve install identity, databases, credential trust/protocol, repository URL and unrelated work. This remains a pilot.

## Analysis before implementation

| Area | Finding | Required change / acceptance |
|---|---|---|
| Branding | Launcher, Android header and web account/sidebar use the old name | SurakshaAr on current surfaces and new APK; old installs and QR records remain compatible |
| Dependencies | npm audit: 24 affected packages, including 1 critical and 16 high | Compatible updates; triage remainder; rerun runtime and integration checks |
| Android navigation | Nine GestureBackNavigation lint errors around legacy Back overrides; callback coverage needs inspection | Confirm existing modern callbacks preserve paging/exit semantics and scope API 29–32 compatibility annotations; verify emulator journeys |
| Web checks | Unit tests (20), typecheck and build pass; lint has 71 errors and 6 warnings | Correct application types, hook behavior and JSX; narrowly justify fixture exceptions |
| Accounts | Recovery absent; password-change forms lack reveal | Maintained-library reset flow, expiry/replay/session tests, accessible controls; delivery must be configured before claiming it works |
| Requests | JSON reader buffers unbounded bodies, checks characters rather than UTF-8 bytes | Stream limit, object/content/origin checks and adversarial boundary tests |
| Native networking | Malformed successful responses silently become empty objects; session/error handling needs review | Explicit failures, expiry handling and scoped encrypted cookies |
| Concurrent writes | Import and issuance use check-then-insert | Atomic idempotency/conflict handling; concurrent requests preserve records and single credential |
| Dashboard state | Refresh failures can retain stale records and errors need clearer recovery | Prevent stale protected actions, recover expired accounts, avoid duplicate submissions |
| Documentation | Older status/tracker contradict newer phone evidence and native upload | Reconcile implemented, historical, pending and tested scope |
| Learning and AR | Five content domains; fire/gas room engines; many architecture plans remain proposals | JVM/emulator regression plus rendered inspection; physical tracking/discharge requires real device |
| External prerequisites | Hosting, reviewed Santali, qualified safety review, production signing and physical device matrix incomplete | Keep visible limitations; record requirements without inventing delivery or approval |

Users: shared-phone learners and admin/trainer/viewer workspace members. Data: learner names and histories, account sessions and issuer key. Existing fixed screens and light theme stay in scope; no unrelated redesign.

## Acceptance plan

Apply rulebook GOV, FLOW, A11Y, AUTH/REC, SEC input/tenant/idempotency controls, NAT lifecycle, ENG/DSO and DONE evidence. Run Android JVM/build/lint/instrumentation; web unit/types/lint/build/API suites; inspect desktop/mobile and emulator output; review dependencies/diff/secrets; commit and push without force. Separate historical evidence from checks run now.

No physical phone is attached at review start. Two Android emulators are available. No production-ready claim follows from emulator results. No external email is sent in tests; mail transport and account hosting need operator provisioning.

## Implemented in 0.8.0

- SurakshaAr launcher, native headers, dashboard/account screens, page titles and build artifact. Android remains `com.narvyn.suraksha`; database names, issuer trust, QR protocol and historical release URLs stay compatible. Version code 20 / version 0.8.0. This is a debug-signed pilot APK, not a production store release.
- Compatible dependency updates including Next 16.3.5, React 19.2.8, Vinext beta.10, Vite 8.3.0 and Cloudflare tooling. npm audit fell from 24 affected packages (1 critical, 16 high, 6 moderate, 1 low) to zero. The scoped esbuild tooling override also passes `drizzle-kit check`. This is an advisory snapshot, not a guarantee against undiscovered vulnerabilities.
- Streamed JSON input with a real UTF-8 byte ceiling, content/origin validation, object-only bodies and adversarial tests. Account requests have a smaller 32 KB limit. Private pages/API responses use no-store and protective response headers.
- Atomic assessment conflict protection in migration 0007; concurrent duplicate imports do not replace evidence or duplicate audit events. Concurrent issuance returns the single stored credential and still rejects attempts to extend an existing assessment’s expiry.
- Shared web/native password recovery entry points, 15–128 character new-password policy, accessible reveal controls, account-neutral responses, 15-minute token expiry, single use and session revocation. Other outstanding links are invalidated after a reset. Recovery requires a configured delivery gateway; no provider was provisioned.
- Browser requests have timeouts and readable error states; expired private sessions return to sign-in. Failed refresh clears protected data. Parent refresh now reloads worker/admin and room-journal panels. Duplicate-submit guards, fresh credential QR state, tab-specific titles, an accessible mobile refresh label and dialog focus restoration were added.
- Native networking now rejects unreadable successful responses, checks cookie expiry and distinguishes rate limits and service failure. Existing encrypted cookie persistence remains in place. Existing API 33+ Back callbacks were confirmed; lint annotations cover the retained API 29–32 overrides. Debug loopback network rules now state domain scope explicitly.
- Production-preview startup now resolves the ignored local secret file with an absolute path, avoiding missing configuration when Wrangler runs the generated config under `dist/server`. No secrets are copied into the distributable build.
- Page-based Android tests replace obsolete scroll assumptions. The repeatable emulator matrix separates normal font, 200% font, permission-off and critical-thermal fixtures. The native account test selects the correct navigation control. GitHub checks were added for web and Android builds, lint and unit tests.

## Verification performed

| Check | Result and limits |
|---|---|
| Web unit | 25 passed, zero failures; grading, expiry, HTTP boundaries and recovery delivery |
| Web types / ESLint / build | Passed; narrow test-fixture lint exceptions remain documented in config |
| npm audit | Zero known vulnerabilities at review time |
| D1 migrations / tooling | Migration 0007 applied locally; drizzle schema check passed |
| Existing API integration | Assessment imports/scoring/archive versions, signed credentials/tamper/revocation, accounts/invitations/roles/tenant isolation/assignments/audit and room journals passed |
| New reliability integration | Concurrent imports and issuance, single audit events, immutable conflicts, password policy, neutral recovery, simultaneous reset, replay, expiry, outstanding-link invalidation, session revocation and new login passed |
| Mail | Loopback-only synthetic `@example.test` stub; no external delivery. Temporary settings removed afterward; configured-service restart verified neutral 503 for known/unknown emails when delivery is unavailable |
| Built web runtime | Production worker pages, security/privacy headers, authenticated session and persisted worker read passed, in addition to development-mode tests |
| Browser inspection | Desktop 1280×720 and mobile 390×844; sign-in, reveal, recovery navigation, menu, worker create/edit, dialog Escape and focus return. No browser console errors in final desktop inspection |
| Android JVM | 157 passed, zero failures or skips |
| Android build / lint | APK and instrumentation APK built; zero lint errors, 36 warnings. Warnings include existing touch-accessibility (7), drawing-allocation (6), custom-view constructor (6), localization (6), and other platform/dependency advisories; no full lint-clean claim |
| Android emulator | 93 passed: 83 ordinary, 2 placement, 2 normal workspace, 4 at 200% text, 2 at critical heat; zero failed groups. AR reference-database test not run because AR services are absent |

The initial undifferentiated Android run exposed stale scrolling tests and missing large-text/thermal fixture conditions. Test assertions were retained and navigation/setup corrected. The matrix explicitly reports absent AR services rather than silently counting an unrun asset test as passed.

## Remaining acceptance gates

1. **Real AR hardware:** no physical phone attached in this review. Historical Samsung evidence in 0.7.1 confirms certain tracking/placement paths only. Complete supported-phone trials for all camera modes, full extinguisher discharge, QR capture, relaunch/backgrounding, heat and representative mid-range hardware. Emulator geometry/recovery tests cannot establish real tracking quality.
2. **Hosted accounts and recovery:** provision the approved production account service, configure HTTPS origins and backend secrets, apply migrations to a backed-up database, and verify actual mail delivery/domain authentication. The localhost gateway is a QA fixture only. Git push is not a hosted deployment.
3. **Safety and language:** qualified review of scenarios, site procedures/emergency information, terminology and Hindi content; native-reviewed Santali translations/audio and worker comprehension remain pending. The app correctly identifies these as pilot simulation records rather than practical certification or permission to work.
4. **Production operations:** release signing/store distribution, approved retention/backup/restore, key rotation and revocation freshness, formal assessor identity and device-loss controls need an operating policy. Native upload/manual import are implemented; automatic resilient background synchronization is not claimed.
5. **Broader accessibility/performance:** current page-navigation and 200% text tests plus selected mobile/keyboard inspection do not constitute full-app TalkBack, all Hindi/audio, representative-worker usability or physical-device performance sign-off.

## Rulebook application and release checks

Applied proportionately to existing learner and admin/trainer/viewer workflows: role checks remain server-side; account and destructive actions stay explicit; failures preserve recovery routes; private data is not cached; concurrent writes preserve evidence; keyboard focus and labels are tested. Existing Mineral Light styling and fixed Android pages were preserved. Atlas form pattern P049 guided task-shaped account forms; no new motion or external visual assets were introduced.

The pilot release gate passes local implementation/build/API checks with all 93 runnable emulator checks passing. The production release gate remains open for the external requirements above. Existing unrelated `docs/solution-blueprint.md`, `apps/hospital-concept/` and `docs/development-rulebook/` work is excluded from this commit.

Primary guidance checked: [Better Auth reset flow](https://github.com/better-auth/better-auth/blob/main/docs/content/docs/authentication/email-password.mdx), [rate limits](https://better-auth.com/docs/concepts/rate-limit), [Android predictive Back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture), and official [Next](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4) / [React](https://github.com/react/react/security/advisories/GHSA-wx67-qw84-cm4g) advisories. Current installed tooling was inspected to diagnose generated-config secret-path resolution.

## Reproduce Android fixtures

Build with `bash scripts/build-android.sh lintDebug assembleDebugAndroidTest`, start the configured account service on port 5173, then run:

```sh
python3 scripts/test-android-matrix.py --serial emulator-5554 --out /tmp/surakshaar-matrix
```

The script refuses physical devices, installs both APKs, reverses local port 5173 and restores original font/animation settings and thermal state. Camera permission is deliberately controlled as a test fixture. The AR reference-database test runs only when Google Play Services for AR is installed; physical tracking remains a separate check.
