# Version 0.6.0 — expanded room training and camera recovery

Updated 16 September 2026. This is a debug-signed pilot, not a verified physical-AR or statutory certification release.

## Delivered

- Native module launchers now prioritise fire/gas room training. Lessons show one page at a time with progress, Back/Next and saved position. Extra modes have their own screen. Lists and large accessibility text may still scroll.
- Fire now connects alarm, clear/blocked exit identification, equipment/evacuation choice, pin/base/sweep interaction, withdrawal, evacuation route, assembly and missing-worker reporting. An announced explosion-risk scenario rejects discharge and requires evacuation; the app does not detect real explosion risk.
- Gas adds outside-role PPE selection, buddy contact and acknowledgement to the existing SIM-meter, boundary, attendant and refused-entry sequence. A dust mask never grants entry into an unknown atmosphere. This is an outside-only draft scenario, not a complete entry or rescue qualification.
- Scene choices use original door, route, assembly, PPE and communication illustrations. They are projected from station anchors in camera mode. Screen practice has separate attribution.
- All four AR entry paths share a 30 fps camera configuration and paced frame requests, thermal pause, actionable startup errors and a process-wide asynchronous session-release barrier. Texture registration follows GL/resume lifecycle; camera actions still require fresh tracked images. Procedure screen rebinding preserves healthy anchors.
- Room practice can resume validated v2 journals after a fresh activity starts. Accepted/rejected actions and assistance history survive; partial gestures and visible hints reset. Camera placement is repeated. V1 history stays readable and cannot be resumed as v2. Clock continuity cannot manufacture camera freshness.
- My record exports room journals separately. The trainer Room practice tab imports v1/v2 records, preserves immutable snapshots, checks action history and distinguishes screen/camera mode and help use. No room record can issue a credential. Existing decision assessment and pilot QR credentials remain separate.

## Verification

Final build passed **143 JVM tests** with zero failures/errors/skips. APK SHA-256:

`ac11ac2f7507617eeb8380e3cb4eebc7ec9f8c84812d3d2a669948e5f915cf2e`

API 36 ARM64 emulator using host GPU:

- 21 methods passed in airplane mode (126.644 s): expanded fire/gas flows, explosion and unsafe choices, recall, storage and native navigation.
- Saved-journal fresh-activity recovery/export passed (10.603 s).
- 14 regression methods passed (130.564 s): AR decision recovery, camera denial/fallback and existing procedure/spatial flows. Camera permission was deliberately denied for this run.
- Four native navigation/recall workspace methods passed at 200% system font (24.691 s), including Hindi portrait/landscape controls. Two critical-thermal recovery methods passed (6.670 s); these use an emulator-only thermal override.
- Three final walkthrough methods passed during recording (51.781 s): complete screen fire, screen gas and explosion-risk evacuation. The 52.24-second release video captures these emulator tests (including lifecycle transitions), not real-camera tracking. Three sampled frames were decoded and inspected.
- Native home, module, Hindi lesson, new scene choices and large-Hindi landscape screenshots were inspected. This is scoped rendering evidence, not a worker usability study.

Dashboard: 20 unit tests, TypeScript checking, room-journal API integration and production build passed. A real Android export containing 97 emulator journals imported through the local API with HTTP 200; no certificates were created. The build retains a large-client-chunk warning. No browser visual test was performed in this increment.

## Still open

1. **Actual phone AR remains unverified.** Only the emulator is currently visible over ADB. Earlier Samsung testing showed camera video without successful tracking/placement. The new runtime changes need a supported physical phone for three-station placement, world stability, both complete camera missions, interruption/recovery and thermal/frame-time checks. Emulator success does not close this defect.
2. Reviewed Santali UI, lessons and audio are absent. English/Hindi safety wording also needs competent review.
3. Machinery has lessons, equipment practice and decision assessment, not a full action-driven room mission. The supplied official description stops at “(3) Machinery”; domains 3–5 and sector-specific variants need confirmation.
4. Room completion is guided practice, not an independent practical assessment. Identity verification, assessor roles, governed practical sign-off and production issuer operations remain incomplete.
5. Dashboard hosting is unresolved. The original Sites registration returned an uncertain transport failure. Current complete site discovery lists no Suraksha site; no duplicate registration was made. Local dashboard/manual transfer work; automatic sync and public verification hosting are not claimed.
6. Real cross-phone QR scanning, Android 10/mid-range performance, offline AR provisioning, process-kill recovery on devices, full accessibility and retention/transfer studies remain acceptance gates.

## Technical basis

Camera changes follow Google's [camera configuration guidance](https://developers.google.com/ar/develop/java/camera-configs), [session lifecycle reference](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#close()) and [Hello AR renderer](https://github.com/google-ar/arcore-android-sdk/blob/master/samples/hello_ar_kotlin/app/src/main/java/com/google/ar/core/examples/kotlin/helloar/HelloArRenderer.kt). These references explain implementation choices; they do not establish that this build tracks correctly on a physical device.
