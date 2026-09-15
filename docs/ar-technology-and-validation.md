# AR technology decisions and validation — 0.5.5 candidate

Research and implementation date: 15 September 2026. This is an engineering increment, not a claim that the full competition solution or physical AR has passed. The public APK remains 0.5.0; 0.5.1 remains a draft. See the [problem-statement coverage report](ar-problem-statement-coverage.md) for the full delivery contract and missing objectives.

## Architecture for this problem

Retain native Kotlin with ARCore and local training data. The primary experience should place metre-scale, interactive training equipment in a clear real room, teach the learner through actions and consequences, then record independent decisions separately from guided practice. A screen simulation remains an explicitly labelled accessibility/device alternative. Android 10 is the minimum OS, but camera AR also requires a supported device and provisioned AR services; it cannot be promised on every Android 10 phone. [Supported-device documentation](https://developers.google.com/ar/devices), [AR availability and installation](https://developers.google.com/ar/develop/java/enable-arcore).

The current stack is suitable for proving these two modules. An engine rewrite would not by itself resolve sensor starvation, incomplete objectives, or missing physical evidence. Keep one rendering/session owner, small local meshes, bounded frame requests, and offline local anchors. Validate this before adding rendering features with larger CPU/GPU costs. Google identifies anchor use, device-specific measurements and CPU/thermal contention as relevant to AR quality. [ARCore performance guidance](https://developers.google.com/ar/develop/performance).

## Implemented in this increment

### Anchor-relative orientation

The previous room renderer reconstructed each station from the anchor's current translation and a saved world-space yaw. The yaw could become inconsistent when ARCore revised world coordinates. The new `RoomAnchorPose` stores the initial camera-facing rotation relative to the anchor, then composes it with the anchor's full current pose every frame. Rendering, target projection and gas outside-side classification now use that same orientation.

Synthetic pose tests apply common translation/rotation changes to the camera and anchor and check that relative points and gas classification remain consistent. These establish the mathematics, not measured physical drift. ARCore documents that world coordinates are frame-local and that persistent positions should be anchored or expressed relative to anchors. [Pose contract](https://developers.google.com/ar/reference/java/com/google/ar/core/Pose), [anchor design](https://developers.google.com/ar/develop/anchors).

### Camera texture and session lifecycle

Camera texture registration now occurs once per session/resume/GL-context generation. Background drawing waits for a nonzero, accepted image timestamp, including after a pause. The prior path registered the same texture on every render and drew even before the first camera image. Session retirement first pauses rendering and removes anchor/session references on the UI thread, then closes the retired session on a background executor. Retry waits for that retirement; a completion callback cannot restart a closed or backgrounded view.

Google documents that re-registering textures and pausing can invalidate image contents, and that session close may take seconds and should occur off the UI thread after pause. These fixes address concrete API misuse; they have not been established as the cause of the earlier Samsung tracking failure. [Session contract](https://developers.google.com/ar/reference/java/com/google/ar/core/Session), [official HelloAR renderer](https://github.com/google-ar/arcore-android-sdk/blob/master/samples/hello_ar_kotlin/app/src/main/java/com/google/ar/core/examples/kotlin/helloar/HelloArRenderer.kt).

### Deliberate placement and real-scale preview

Before placement, the view outlines the selected detected surface and the virtual station's footprint. A valid candidate displays the actual metre-scale equipment/hazard/marker with a prominent “PREVIEW · NOT PLACED” label. It has no action targets or training credit. Teal indicates placement readiness; amber indicates a candidate that is not yet ready. Station previews draw after placed objects so their depth buffer is not erased by the equipment pass. Surface polygons are clipped against the viewing volume before projection, so large detected planes stay visible even when their original corners are offscreen. The preview warning uses measured font bounds and stays within its Canvas area at 200% text size.

The button requires the footprint to fit the detected plane, a viewing distance of 0.6–3m and a consistent station level within 20cm. It also requires 450ms of stable candidate observations, at least five distinct camera frames, gaps no larger than 150ms and movement within 12cm of the initial surface-local point. Duplicate frames cannot extend freshness or build dwell. Tracking, viewport/revision and existing-anchor guards remain in force at commit. Trackable keys use equality because ARCore may return distinct Java wrappers for the same native plane. Surface changes, invalid hits and interruptions reset readiness.

The range, height and dwell values are app interaction choices to evaluate with workers; Google does not prescribe these numbers. They are not operational safety distances. Plane detection does not recognise “floor,” inspect obstacles, verify a safe training area, or identify real exits. Users still select a clear floor. The design follows Google's recommendation to explain scanning, visualise the selected surface and destination, bound placement range and provide recoverable feedback. [Placement guidance](https://developers.google.com/ar/design/content/content-placement).

### SDK maintenance

Updated the bundled ARCore SDK from 1.50.0 to 1.56.0 after checking Google's release notes. The app retains explicit `minSdk=29` and `targetSdk=36`; the dependency's target API change does not silently set the app target. SDK updates are tested here as compatibility changes, not claimed tracking cures. [Official release notes](https://github.com/google-ar/arcore-android-sdk/releases).

## What makes the next AR stages valuable

| Next stage | Concrete implementation and proof | Status |
| --- | --- | --- |
| Verified physical foundations | Repeated cold starts, three placements, panning/return, interruptions/retry and full fire/gas completion on the Samsung and a representative mid-range Android 10+ phone. Record time to placement, tracking loss, frame cadence, thermals and failed attempts. | Required; no physical phone connected during this increment |
| Complete fire training | User-marked practice exit, exit identification, suitable-role/equipment decision, evacuation-only branch, ordered accountability, and response to worsening/explosion-risk cues. Use reviewed scenario controls; never infer a real safe route from a camera plane. | Missing objectives; see coverage report |
| Complete gas training | Enacted PPE selection, prerequisite/permit review and buddy communication in the same AR flow, plus exclusion and outside-only refusal when prerequisites fail. Keep the meter visibly simulated. | Some content exists elsewhere; coherent AR integration required |
| Material realism | Authored, licensed glTF equipment with correct scale, recognisable controls, compressed textures, mesh/texture budgets and device profiling. Optional environmental lighting and contact shadows, with measured fallback to current lightweight shading. | Proposed; current meshes remain illustrative |
| Depth occlusion | Capability-check Depth API, handle temporarily unavailable images, release every acquired image, transform display/depth coordinates correctly, and make equipment disappear behind real objects only with valid depth. Test known distances and edge cases. | Proposed; not enabled or claimed |
| Printed training station | Optional feature-rich, flat printed scene card of known physical size using Augmented Images; require full tracking and quality-check the reference. Disable unused tracking features. | Proposed; not a QR tracking shortcut |
| Reproducible AR debugging | Opt-in ARCore dataset recording/playback for rendering/lifecycle regressions. Keep camera datasets local and label playback evidence. Live field acceptance remains separate. | Proposed; no surroundings recorded in this increment |
| Trainer evidence | Export/import versioned room/procedure journals with assistance, interruptions and presentation mode; replay before review. Independent assessment and practical observation stay distinct from guided completion. | Required integration |

Depth can improve visual occlusion on supported devices and does not require a dedicated ToF sensor on every device. It is an optional realism tier; requiring it universally would reduce device reach. [Depth implementation guide](https://developers.google.com/ar/develop/java/depth/developer-guide).

Environmental HDR provides directional light, spherical harmonics and a cubemap; it needs an appropriate material/rendering pipeline. Merely enabling that API would not make our current illustrations photorealistic. [Lighting guide](https://developers.google.com/ar/develop/java/lighting-estimation/developer-guide).

Augmented Images runs on-device and can work offline. Google recommends feature-rich reference images and sufficient initial image coverage; QR codes/line art are poor references for this API. Therefore certificate QR and any future AR training card serve different purposes. [Augmented Images guide](https://developers.google.com/ar/develop/augmented-images).

ARCore playback may produce different trackables/poses across runs and is not an exact deterministic sensor oracle. It would supplement real-device tests, not replace them. [Recording and playback](https://developers.google.com/ar/develop/java/recording-and-playback/developer-guide).

## Learning and certification contract

A complete module needs demonstration, guided enactment, fading help, an independent changed scenario, critical-error feedback and delayed retrieval. Realistic objects should make the relevant controls and spatial consequences easier to understand. Score the action and decision that represent the objective; do not reward merely staring at a model or passing time. Measure delayed performance and trainer-observed transfer before making retention claims. The [learning evidence document](ar-learning-evidence.md) and [coverage report](ar-problem-statement-coverage.md) distinguish evidence-informed design from unmeasured outcomes.

Reviewed Hindi and Santali/audio, offline provisioning, a governed issuer, a hosted trainer dashboard, independent/practical assessment policy and a matching real-phone demo remain required for the complete submission. This engineering increment does not substitute for those deliverables.

## Read-only device diagnostics

With the app open on a USB-authorised phone, run:

```sh
python3 scripts/ar-device-report.py --output artifacts/ar-device-report.json
```

Use `--serial` to select between connected devices. The report records OS/app/AR-service versions, thermal status and a bounded set of this app's RoomAR diagnostics. It does not install or launch anything, capture surroundings or declare an acceptance pass. Buffered messages can span earlier sessions and must be correlated with the current build. Emulator reports require explicit `--allow-emulator` and remain labelled as emulator evidence.

## Validation record

- Final APK/test APK build succeeded with ARCore 1.56.0; **118 JVM tests passed**, zero failures/errors/skips. This includes new stable-frame readiness, footprint/range and homogeneous clipping regressions.
- **Eleven Android test methods passed** on the API 36 arm64 emulator with host GPU graphics. Seven combined methods covered pose rebasing, four preview-overlay fixtures, complete fire/gas screen missions, all equipment recognition/recreation and existing spatial procedures (100.383s). Thermal/retry checks passed (two methods, 6.528s), Hindi 200% portrait/landscape passed (7.059s), and camera-denial/recreation passed (4.713s).
- Inspected English/Hindi synthetic preview overlays at 200% and final gas/Hindi mission captures. The overlay fixtures use no camera Session and cannot authorize actions. They are not photographs of real plane detection or equipment placement. Local captures are under `artifacts/ar-055-qa/`.
- The read-only device report ran successfully against the explicitly labelled emulator; its default physical-only path correctly refused to treat that emulator as a phone. Python compilation and `git diff --check` passed. Thermal override/font/permission settings were restored after checks.
- Final candidate: `artifacts/suraksha-saathi-0.5.5-debug.apk`, version code 15, debug-signed. SHA-256: `c94a52052beaae8f0e3d6b0a65a5b9f0993506df1611db09a53f9816f4616514`.

Physical tracking, initial plane discovery, actual full-size preview placement, gas outside-side behavior and session-close latency cannot be certified from emulator screen/pose tests. ADB showed only the emulator at completion; no physical-phone install/test or real-camera demo was performed. No public APK release was promoted.
