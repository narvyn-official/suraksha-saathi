# Pilot validation — 14 September 2026

## v0.3.0 verification

- Android debug APK and instrumented-test APK built successfully. **12 JVM tests** passed: five grading tests and seven recall scheduling/content-compatibility tests.
- **Seven Android instrumented tests** passed on an API 36 ARM64 emulator in the final recorded run (43.331 seconds). Coverage includes fire assessment across activity recreation, Hindi home rendering, offline credential verification/tamper rejection, machinery critical-stop behavior, pixel-verified rendering/rotation/zoom of all four 3D models, PPE practice and assessment in English and Hindi, and recall persistence across recreation without modifying assessment/export evidence.
- Fresh screenshots of fire, gas, machinery and PPE equipment were visually inspected. The spaced-review feedback screen and Hindi home were also inspected. These show detailed illustrative geometry and material highlights, not manufacturer-accurate photoreal assets.
- **Six web analytics/grading tests**, TypeScript checking and the web production build passed. The build retains a large-client-chunk warning.
- Dashboard API integration passed, including PPE credential issuance/verification, archived 0.2 import and rejection of PPE falsely labelled as 0.2. Existing checks cover unauthenticated access, repeat import/issuance, score replay, altered records, unknown options, signature tampering, revocation, sector persistence and failed-assessment issuance rejection.
- Android/dashboard curriculum mirrors match: four modules, 32 unique decisions. The three earlier modules are unchanged and the 0.2 archives match the prior curriculum exactly. Trust files remain unchanged.
- The APK SHA-256 is `cebfb6d237b5e96877812daa4034c7cbe6fd0ae0f22a08913386019cc799e985`.

The v0.3 MP4 is a 50-second emulator capture around the instrumented walkthrough. It demonstrates app screens and equipment rendering; it does not demonstrate real camera tracking or a field trial. Local integration records are explicitly named “Demo learner · test record”.

## Earlier evidence retained

The v0.2 release passed five JVM tests, five web tests and five Android instrumented tests. Browser inspection then covered populated trainer records, verification success, responsive layout, sector filtering, worker history, critical-decision review and WebMCP inputs. Those browser checks were not repeated for v0.3; current web changes were checked through tests, APIs, types and production build.

Earlier validation found dialog-focus selectors, status-bar overlap and excessive viewer zoom; these were corrected. The final v0.3 instrumentation ran on its own, without competing Android installation or instrumentation sessions.

## Not completed

- Android lint did not complete: downloading lint's IntelliJ/Kotlin dependencies stalled, and an offline retry confirmed those dependencies were unavailable. Build and instrumented results above passed independently.
- No physical Android 10 or ARCore phone was connected. Real tracking, camera permissions, lighting estimation, device recovery, offline gas/fire AR paths, QR camera scans, thermal/battery performance and environmental robustness remain unverified.
- No native-reviewed Santali pack or professional safety approval.
- Sites registration returned a transport failure. Discovery found no corresponding site. No hosted URL, automatic sync or public revocation endpoint is claimed.
- No delayed retention or practical-transfer study. Spaced-review intervals are pilot heuristics; no retention improvement or accident reduction has been measured.
- No adversarial security review, production organisation roles or production signing/distribution audit.
