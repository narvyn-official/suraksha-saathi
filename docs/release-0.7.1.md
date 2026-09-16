# 0.7.1 — Fixed screens, spoken learning and AR placement

This Android 10+ pilot uses explicit steps and popups throughout the native learner and admin workflows. Important controls stay visible; long content uses **Earlier / More**, while learning popups have one **Previous / Close / Next** footer. Large text remains readable, and changing parts preserves form input. Native operating-system pickers retain their own behavior.

## Changes

- Automatic instructions default to on, use the selected installed offline English/Hindi voice, and advance with the learning task. Replay and persistent mute controls remain available. Recall speech omits unrequested guidance.
- Android system Back returns through app screens and closes popups. Leaving unfinished room practice offers to save and return; saved camera attempts require replacement of their anchors on resumption.
- Placement accepts a mapped 15 cm anchor patch rather than requiring the entire virtual model inside a mapped plane. Unmapped parts retain a visible warning. Fresh tracking, range, dwell and spatial checks remain enforced.
- Oriented virtual equipment footprints require a 20 cm gap, in addition to prior centre-distance and gas-barrier rules. This prevents model overlap; it is not an industrial safe-distance calculation.
- Always-accessible placement help explains automatic scanning and recentres the placement cursor. The separate **Place station** button is enabled only when placement is ready.
- Twelve fire-learning topics include seven interactive fire-type/agent comparisons, equipment inspection, PASS, stop/withdraw decisions and assembly/reporting. Coaching adds reasons, do-not guidance, self-explanation and evidence-based personal review.
- One native AR driver owns session updates across the four camera paths, with a camera lease, frame-freshness guards and recovery diagnostics. The tested Samsung sensor-rate permission fix is included.

## Verified

- Debug APK and test APK build; **157 JVM tests pass** with no failures/errors/skips.
- **27 targeted emulator UI tests pass**, covering navigation, preserved input, whole-button pagination, Hindi coaching at 200% text in landscape, narration behavior, recall, fire/gas journeys, camera recovery UI and placement rendering.
- The fixed-screen candidate was installed on the Samsung SM-S921B without clearing data. Home layout, internal system Back, Hindi placement help, two real anchors, rejection of too-close virtual footprints and tracking recovery were checked. Logs report offline English/Hindi playback; the user confirmed automatic Hindi instructions are audible.
- Earlier same-day checks confirmed stable three-anchor placement while moving and looking away/back, a six-action fire evacuation branch, and a seven-action outside-only gas branch. These are practice records, not practical certificates. The final candidate has not repeated both complete branches.
- APK signature and published SHA-256 checksums accompany the release. The binary is **debug-signed**, version code 19.

## Remaining limits

The fire pin/aim/sweep/discharge branch still needs a full physical run. A supported mid-range device matrix, measured long-duration performance and reviewed Santali content/audio remain pending. This update adds no complete machinery AR mission, Filament/GLB renderer or active printed-image setup. Broader architecture documents describe future work as well as implemented work.

ARCore compatibility is required for camera AR; Android 10+ alone is insufficient. Initial AR service provisioning and installation of a matching offline voice may need a connection. Text remains available when a voice is unavailable. Models do not detect actual fire classes, gas, safe exits or physical clearance.

Public account hosting remains pending; native admin login requires a configured account server. Pilot credentials do not establish statutory certification or permission to work. Safety-trainer review, language review and measured learning effectiveness are still required before field deployment.

See the [detailed learning update and sources](learning-update-2026-09-16.md) and [AR investigation](ar-sensor-rate-investigation.md).
