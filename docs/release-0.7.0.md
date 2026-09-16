# 0.7.0 — Native workspace and compact navigation

[Download the 0.7.0 APK](https://github.com/narvyn-official/suraksha-saathi/releases/download/v0.7.0-pilot/suraksha-saathi-0.7.0-debug.apk), version code 18, Android 10+. Debug-signed pilot prerelease. [Release and checksums](https://github.com/narvyn-official/suraksha-saathi/releases/tag/v0.7.0-pilot).

## Changed

- Learner home shows the selected module, progress and one primary learning action, with a module picker and fixed bottom navigation. Long lesson stacks become separate lesson steps, with Previous/Next actions pinned below the reading area. The divider now has an explicit height so it cannot consume the content area.
- Light teal actions, slate surfaces, functional colour accents, native vector icons, clear selected tabs and touch feedback are shared across the app.
- Native training-centre login and administration are accessible from the learner profile menu and Help. Email/password signup, login, session restoration, sign-out and password changes use Suraksha accounts, independent of ChatGPT.
- In-app workers, learning history, batch assignments, cancellation, report sharing, assessment decisions, room journals, QR credentials, verification, team access, centre settings and activity history connect to the same backend as the companion dashboard.
- The browser companion now has a persistent sidebar, grouped destinations, an account menu, centre selector and focused account entry. Worker register/history have separate views; new assignments open in a focused dialog.
- Database migration 0006 adds account/session storage and invitation hashes. Invitation acceptance requires the private one-time code and matching email; permissions and revocation are checked server-side.

## Verified

- Android APK build and all 143 unit tests passed.
- 15 emulator checks passed: native account flow and stored session, worker creation, sign-out; English/Hindi home fit and navigation; learner assessment/emergency flows. The normal-font home test confirms no downward scrolling is required and key controls are visible.
- Native account smoke test passed again after native workspace changes.
- Learner navigation also passed at 200% system text size after enlarging/stacking controls; the standard font setting was restored.
- TypeScript, all 20 web unit tests, and production build passed. Existing large client-chunk warning remains.
- Four API integration suites passed: independent accounts, admin permissions/assignments, assessment/credential records and room-practice journals. Native screenshots inspected; no browser UI test performed.

## Limits that remain

The native admin workspace was tested against localhost through emulator port forwarding. The public backend is not deployed: Sites registration remains unresolved. Use Connection settings with your centre's HTTPS origin when a service is available. Offline training remains usable without a server.

No physical ARCore phone is currently connected; this release does not claim to fix or validate real-world tracking. Santali native-speaker review/recordings, practical assessor sign-off and official certification remain pending. Phone evidence is not hardware attested. Account email verification/forgotten-password recovery, automatic assignment delivery and background synchronization are not configured. Some web analytical filters do not yet have native equivalents.

See [account setup, workflows and migration details](admin-workspace.md).
