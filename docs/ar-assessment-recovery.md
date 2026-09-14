# Recoverable AR decision stations

App 0.4.2 hardens the existing guided/scored decision stations. It shares the image-freshness and lifecycle gate with fire component practice, without changing curriculum 0.4.0, equipment geometry, grading rules or credential scope. These are simulated decision choices; they do not detect real hazards or measure physical equipment operation.

## Keep learning feedback available

A guided answer is saved before its explanation appears. The explanation remains available through activity recreation and camera loss until the learner explicitly selects Continue. An assessment instead shows a neutral saved-answer acknowledgement: the explanation stays hidden until the attempt ends. A critical assessment error still ends the attempt immediately under the existing rubric.

The last guided explanation also requires acknowledgement before completion. Returning to screen mode reloads the same saved answer, preserves feedback and marks the attempt as hybrid. Missing camera permission does not block reading saved feedback or switching to the screen alternative. Camera permission is requested only by the explicit Enable camera action.

This supports corrective practice and self-paced processing described in [the learning evidence review](ar-learning-evidence.md); it is not evidence of measured retention gains.

## Reject stale camera interactions

The UI owns assessment mutations. The renderer receives an immutable scene description, not a mutable assessment JSON object. Question IDs and runtime revisions travel with queued choices. Question transitions, placement changes and lifecycle changes invalidate old choices; duplicate answer/Continue callbacks cannot add another event or skip a question.

A new answer requires an active activity, a fresh camera image, a tracking camera and anchor, projected stations inside the viewport, and fully visible native answer controls. An unchanged camera timestamp does not become fresh just because another render frame was drawn. Resuming requires a newer image. Tracking loss clears pending choices and placement; a stopped anchor is detached. A queued choice is checked again against the next camera frame and current UI revision before persistence.

An explicit camera retry stops rendering before pausing/closing the old session, resets its image freshness, and permits another installation request after refusal. Permission settings and screen fallback remain available. The unavailable-camera viewport is light; the overlay becomes transparent when the camera starts, so it does not cover the placement view.

## Readable controls and storage behavior

Answer labels are native buttons measured at the current text size. The layout uses two columns when appropriate or a vertical stack, connects controls to projected stations with leader lines, and rejects a layout that cannot fit. It never deliberately truncates an option to create a tappable station. Controls have at least 48 dp height and native accessibility semantics. Full large-font/TalkBack and physical-phone validation remain pending.

These are adaptive screen-space callouts connected to world-anchored stations, not mesh hit-testing or precise manipulation handles. When choices cannot fit, the learner can continue on screen.

Answer and Continue operations clone the current training state, persist the candidate, and replace the live state only after the save returns successfully. A thrown persistence failure leaves the controller's live state intact and surfaces a retry notice. This is tested with injected save failures; it is not a device-storage exhaustion or crash-atomicity test of every SQLite failure mode. Archived attempts resolve their own bundled curriculum version when resumed.

## Evidence and remaining checks

See [validation](validation.md) for exact test counts, inspected screens, APK checksum and the emulator walkthrough. JVM tests cover stale/question/lifecycle callbacks, duplicate actions, feedback restoration, final acknowledgement, critical stops, invalid options and persistence exceptions. Native tests seed saved AR records and verify recovery, English/Hindi feedback, camera-off hidden targets and return to screen mode. They do not simulate positive physical camera tracking.

Before a field pilot, test actual ARCore placement and scale, rotation, lighting, movement out of frame, tracking loss, Android installation/permission settings, pause/resume and device performance. Repeat with representative font sizes and assistive technology. Component and procedural curriculum still need competent safety/language review; phone decision success is not practical qualification.

## Primary technical references

- [ARCore update modes](https://developers.google.com/ar/reference/java/com/google/ar/core/Config.UpdateMode): latest-image mode can return the previous frame when no new camera image exists.
- [Android activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle): separate lifecycle-controlled resources from persisted learner state and handle recreation.

References checked 15 September 2026. The runtime guard and feedback interaction above are this project's implementation choices, not guarantees supplied by those APIs.
