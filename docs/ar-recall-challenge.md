# Remember, then do — room mission recall challenge

Implemented in the 0.5.6 development candidate on 15 September 2026. This adds a rehearsal mode to the existing fire and gas room missions. It does not establish successful physical-camera AR, independent practical competence, or certification readiness.

## Learner experience

Open a fire or gas lesson and select **Remember, then do · AR challenge**. The existing guided mission debrief also offers **Try without cues**; mission Options provides another entry point. English and Hindi are implemented.

Learners use the same equipment gestures and scene consequences as guided practice. Fire covers alarm, pin preparation, steady base alignment, continuous sweep and withdrawal when conditions worsen. Gas covers a simulated meter check, exclusion barrier, outside attendant and refusal of entry when rescue readiness is unconfirmed. This increment adds no new safety procedure or operational threshold.

Recall hides action target rings, the highlighted sweep guide, offscreen target direction, and step-by-step gesture instructions. Goal prompts, equipment, the camera aiming cross, progress, discharge controls and scene consequences remain visible. This is supported recall practice; it is not a fully unguided assessment. Placement instructions and the clear-area briefing stay visible.

An amber **Hint** reveals the current step's instructions and rings for seven seconds. A help request is saved before the hint is exposed. Cues disappear on expiry, step change, focus loss, pause or options. Interrupted gestures must restart; seeking a hint cannot carry partial gesture progress forward. At 128 recorded hints, the control offers a new guided rehearsal instead of silently failing.

The debrief lists the steps where hints were requested and their counts. A zero-hint attempt says only that no coaching hints were requested. Learners can start another recall or guided attempt; each has a new record. There is no targeted phase jump, automatic mastery score or new spaced schedule in this feature.

## Records and recovery

`RoomCoaching` stores version 1, mode (`guided` or `recall`), and an ordered list of `{phase, at}` cues. `at` is elapsed realtime in milliseconds for the attempt, not a calendar timestamp. The helper rejects invalid phases and backwards clocks, caps records, and exposes detached copies. Transient hint visibility is not serialized.

`RoomMissionStore` validates coaching metadata transactionally with the mission. An existing attempt cannot switch coaching mode, delete earlier cues or rewrite their prefix; additional valid cues may append. Old records without coaching remain readable as guided practice. There is no database version migration or retroactive rewrite.

Activity recreation preserves accepted actions and hint history while hiding any revealed cue and resetting incomplete motion. Existing room missions do not resume execution after process death: the saved journal remains available, but a new launch begins a separate attempt. Records remain local and worker scoped. They are not yet exported to the trainer dashboard or used for credential issuance. Camera and screen presentation remain separately attributed.

## Rationale and limits

The feature is designed to let learners attempt a response from memory, ask for focused support, then identify what to rehearse. Original experiments on retrieval and transfer motivate testing this approach, but do not prove effectiveness for industrial AR or this app: [The Testing Effect and Far Transfer](https://pubmed.ncbi.nlm.nih.gov/28082930/) and [Retrieving and applying knowledge to different examples promotes transfer of learning](https://pubmed.ncbi.nlm.nih.gov/29265856/).

Contextual cues and simple interaction affordances also follow [Google's AR interface guidance](https://developers.google.com/ar/design/interaction/ui). Hiding cues is optional, not a general accessibility rule. Worker testing must assess whether seven seconds is sufficient and whether goals, touch targets and help discovery remain understandable. No retention percentage or safety-performance improvement has been measured.

## Verification

Final debug APK: `artifacts/suraksha-saathi-0.5.6-debug.apk`, versionCode 16, ARCore 1.56.0. Build with `bash scripts/build-android.sh assembleDebugAndroidTest`.

SHA-256: `11af6f0eb75487f86e31317d88fc4504f86f8acb8346585d85fd0f821aa15aed`.

- **128 JVM tests passed**, zero failures/errors/skips. Ten new helper tests cover cue timing, validation, lifecycle clearing, copies, cap and serialization.
- **14 Android methods passed together in 73.54 seconds**: three recall flows, two existing guided flows and nine store checks. Full fire and gas runs inject touch gestures at rendered coordinates. They cover no-hint fire completion, gas assistance/expiry, recreation, focus/pause redaction, accepted actions and non-certifying records. They do not directly invoke mission completion callbacks.
- **One Hindi recall workspace test passed at 200% system font in 10.22 seconds**, checking portrait, landscape, usable scene/control bounds and options recovery. Fresh renders were inspected; narrow portrait titles ellipsize while the scene and controls remain visible. Content panels can scroll.
- **Two emulator-only thermal/retry methods passed in 8.285 seconds**, including interrupted discharge recovery; **one camera-denial method passed in 3.903 seconds**. Thermal override, font setting and camera permission were restored.
- Screenshots are under ignored local `artifacts/ar-056-qa/`. These are screen-simulation and recovery evidence, not positive camera AR. No dashboard source changed; web checks were not repeated.

Only the API 36 hardware-accelerated ARM64 emulator was connected for this increment. The final APK has not been installed or completed on a physical phone. Prior Samsung camera-feed-only failure is still unresolved as a validation result. Follow [device validation](ar-device-validation.md) for actual station placement and full camera missions. Public downloads remain 0.5.0; this development candidate is not promoted as an AR-verified release.

The feature addresses reduced coaching and assistance-aware rehearsal. It does not close missing fire exit/evacuation objectives, gas PPE/buddy procedures, reviewed Santali, practical assessment, trainer integration or other gates in the [problem-statement coverage report](ar-problem-statement-coverage.md).
