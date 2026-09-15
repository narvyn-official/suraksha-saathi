# Fire component practice in camera AR — updated for app 0.4.4

This pilot extends the existing component-recognition session into an optional ARCore camera view for the **fire model only**. The same three generic parts, two model orientations, examples, hints, feedback and local review schedule are used. The catalogue remains version 1 and scored curriculum remains 0.4.0. Other models retain screen practice. This implementation has software tests, but no physical-camera validation yet.

## Worker flow

Lesson → Explore in 3D → Practice finding parts → **Use camera AR · fire**. Camera permission and Google Play Services for AR are required. When permission is denied, **Camera permission settings** opens this app’s Android settings; the worker can enable it there or keep using screen practice. From a clear training position, aim the camera centre at a tracked horizontal tabletop and select **Place at camera centre**, or tap the camera view directly, to place the illustrative extinguisher. It faces the camera at placement and turns between the two recognition stages; walking around is not required.

Three native 48dp letter buttons connect by leader lines to model-space parts. Labels are shown for examples, help and feedback, and removed for independent recognition. These are projected recognition targets, not direct mesh manipulation, object detection, instrument readings or physical operation. The app does not measure occlusion by real objects.

**Continue on screen** remains available without a tracked camera. Text descriptions are also available; switching to them pauses camera rendering. All modes use one in-memory session and the existing SQLite learning record, avoiding competing activity copies. Returning or recreating the activity preserves stage, feedback, help, responses and due dates. After recreation, camera placement must be established again. The records remain excluded from assessment exports and credentials.

## Interaction and recovery rules

- New answers and hints require current camera and anchor tracking, a front view, acceptable elevation and all three projected targets in the visible viewport. A rejected tap creates no answer or learner error.
- From app 0.4.3, example and saved-feedback advancement require foreground state, not camera tracking. Scrolling to read feedback must not disable its Continue action. These transitions add no answer or visual-exposure evidence; the next camera answer remains gated.
- Tracking eligibility expires after 500 ms without a new camera image. Repeated renders of the same `Frame.timestamp` do not renew it. Resume requires an advancing timestamp; a cached pre-pause image cannot become fresh again.
- Marker and uncertain-response requests are checked on the next GL frame and again on the UI thread. Scene revisions change on stage changes, placement reset and lifecycle changes; old queued choices cannot apply to a new stage.
- Camera/anchor loss and exceptions hide markers and clear pending choices. A stopped anchor is detached. **Place model again** resets placement without resetting learning. **Retry camera** permits a new user-requested AR installation attempt after a refusal.
- The camera rendering surface is paused when not in use, and the session is explicitly closed when the activity is destroyed. This does not yet establish measured thermal or battery performance.

These constraints prioritize readable recognition from a stationary position. They do not establish dimensional accuracy, robust real-world tracking or practical competence.

From app 0.4.4, native placement and direct taps share expiring, viewport-bound requests. A miss preserves an existing anchor; the separate reset still clears it. The running camera remains visible before placement. See [native placement](ar-placement.md) for implementation and validation limits.

## Attributable learning

Events retain the existing `mode` field (`visual-markers`, `description` or description-exposed `mixed`) and add `presentation` (`screen`, `camera`, `mixed` or `description`). Actual visible model exposure is recorded immediately, not merely selection of a camera button. Both-view exposure remains sticky through recreation and guided retry. Exposure resets only when moving to a different recognition decision.

Successful unhinted visual responses add separate camera, screen or mixed-presentation counters. Any description exposure prevents camera-only visual credit. Existing visual/description totals remain compatible. Older completed events are not relabelled; older active records conservatively retain screen exposure. The summary labels these as new records because historical counters cannot be reconstructed reliably.

The original independent-practice, spacing and assistance rules remain unchanged. This is personal recognition practice, with device-clock scheduling, not certification evidence or a validated measure of spatial skill.

## Technical references and acceptance gates

Checked 15 September 2026 against current primary sources:

- [ARCore session configuration and explicit closing](https://developers.google.com/ar/develop/java/session-config).
- [Tracking states](https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingState).
- [LATEST_CAMERA_IMAGE may return an existing frame](https://developers.google.com/ar/reference/java/com/google/ar/core/Config.UpdateMode).
- [Frame timestamp](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#public-long-gettimestamp).
- [User-requested installation retry](https://developers.google.com/ar/reference/java/com/google/ar/core/ArCoreApk).
- [Anchors and tracking](https://developers.google.com/ar/develop/anchors).

Unit tests exercise attribution, stale frames, pause/resume and revision invalidation. Emulator tests exercise permission denial, screen/text fallback and saved practice. They do **not** validate positive camera tracking or ARCore installation UI on real devices. See validation.md for exact runs.

Physical ARCore testing must cover placement, marker alignment, the changed orientation, readable callouts at target font scales, partial viewport clipping, tracking/permission interruptions, installation retry, offline use and thermal/frame-time performance. Safety and Hindi text remain drafts; Santali and measured delayed transfer remain separate review/evaluation gates.
