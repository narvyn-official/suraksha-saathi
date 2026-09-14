# Pilot validation — 14 September 2026

## Passed

- Android debug APK build and five JVM grading tests.
- Three Android instrumented tests on an API 36 ARM64 emulator: complete fire assessment across activity recreation, Hindi home rendering, and offline credential signature verification with tamper rejection.
- Rendered English/Hindi home screens and assessment result inspected from instrumented screenshots.
- Web production build and TypeScript check.
- Dashboard API integration: unauthenticated rejection; import and repeat import; server score replay; changed-record and unknown-option rejection; credential issuance and repeat issuance; signature tamper rejection; revocation.
- Browser checks: populated trainer records, verification success, responsive layout, WebMCP valid credential and invalid-type rejection.
- Shared Android/dashboard curriculum and trust files match.

The demo MP4 is a short emulator test run. It demonstrates app screens and guided decisions; it does not demonstrate real camera tracking or a field trial. The test records in the local dashboard are explicitly named “Demo learner · test record”.

## Not completed

- Android lint did not complete: downloading lint's IntelliJ/Kotlin dependencies stalled, and an offline retry confirmed those dependencies were unavailable. Build and instrumented results above passed independently.
- No physical Android 10 or ARCore phone was connected. Real tracking, camera permissions, device recovery, offline gas/fire AR paths, QR camera scans, thermal/battery performance and environmental robustness remain unverified.
- No native-reviewed Santali pack or professional safety approval.
- Sites registration returned a transport failure. Discovery found no corresponding site. No hosted URL, automatic sync or public revocation endpoint is claimed.
- No long-duration/offline field study, adversarial security review, production organisation roles or production signing/distribution audit.

A later recording replay exposed intermittent Espresso dialog-focus failures. Dialog selectors were constrained to dialog roots; the final full rerun passed all three tests in 4.163 seconds.
