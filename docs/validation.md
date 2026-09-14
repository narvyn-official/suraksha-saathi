# Pilot validation — 14 September 2026

## Passed

- Android debug APK build and five JVM grading tests.
- Five Android instrumented tests on an API 36 ARM64 emulator: complete fire assessment across activity recreation, Hindi home rendering, offline credential signature verification with tamper rejection, immediate machinery critical-failure handling, and pixel-verified rendering/rotation of all three original 3D models.
- Rendered English/Hindi home screens, equipment viewer and assessment result inspected from instrumented screenshots.
- Web production build and TypeScript check. Five analytics/grading tests cover latest-failure precedence, practice exclusion, three-module completion, revoked credentials, CSV formula protection, critical failures and archived curriculum.
- Dashboard API integration: unauthenticated rejection; import and repeat import; server score replay; changed-record and unknown-option rejection; credential issuance and repeat issuance; signature tamper rejection; revocation; sector persistence; machinery credential issuance; legacy import compatibility; new-module rejection under an old version; failed-assessment issuance rejection.
- Browser checks: populated trainer records, verification success, responsive layout, sector filtering, worker history, critical-decision review, WebMCP valid credential and invalid-type rejection.
- Shared Android/dashboard curriculum and trust files match.

The demo MP4 is a short emulator test run. It demonstrates app screens and guided decisions; it does not demonstrate real camera tracking or a field trial. The test records in the local dashboard are explicitly named “Demo learner · test record”.

## Not completed

- Android lint did not complete: downloading lint's IntelliJ/Kotlin dependencies stalled, and an offline retry confirmed those dependencies were unavailable. Build and instrumented results above passed independently.
- No physical Android 10 or ARCore phone was connected. Real tracking, camera permissions, device recovery, offline gas/fire AR paths, QR camera scans, thermal/battery performance and environmental robustness remain unverified.
- No native-reviewed Santali pack or professional safety approval.
- Sites registration returned a transport failure. Discovery found no corresponding site. No hosted URL, automatic sync or public revocation endpoint is claimed.
- No long-duration/offline field study, adversarial security review, production organisation roles or production signing/distribution audit.

A later recording replay exposed intermittent Espresso dialog-focus failures. Dialog selectors were constrained to dialog roots; the final full rerun passed all three tests in 4.163 seconds.

## v0.2 verification notes

The final direct Android instrumentation run passed five tests in 14.521 seconds. An earlier overlapping Gradle/direct test invocation interrupted instrumentation; the runs were separated. A new test initially expected a Continue control after a critical failure; the app correctly finished immediately, and the test was corrected. Visual inspection found a status-bar overlap and excessive close zoom in the new viewer; both were fixed and screenshots rechecked. The earlier lint dependency limitation still applies.
