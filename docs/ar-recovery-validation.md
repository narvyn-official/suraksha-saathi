# AR recovery candidate 0.5.3

## Why this patch exists

A physical Samsung SM-S921B opened the live camera but stayed at the first station with tracking paused. No floor placement, anchor stability, or completed camera mission was verified. App-scoped ARCore logs showed delayed motion-sensor poses and skipped estimator frames. Android reported severe/critical thermal throttling. This is evidence of a failing test, not proof that temperature was the sole cause.

## Changed runtime behavior

- Select a supported 30 fps camera configuration without hardware depth capture, preferring the smallest available texture. Enable autofocus. Rendering remains paced; severe thermal conditions reduce requests to 20 per second. These are workload controls, not measured device FPS guarantees.
- Distinguish initialization, missing camera frames, insufficient detail/light, excessive movement, competing camera access, and internal ARCore failure. Record scoped `RoomAR` status/cadence logs without camera images, poses, worker identity or surroundings.
- Hit-test the center continuously during placement. Display a projected surface ring and enable **Place station** only for a recent tracked, separated horizontal-plane hit. Placement still revalidates the current frame and lifecycle revision when creating the anchor. This finds horizontal geometry; it does not recognize the semantic meaning of a floor or real equipment.
- Pause camera activity at Android critical thermal status, internal tracking failure, or 45 seconds without recoverable tracking. Offer **Retry camera**. The paused reason survives Options/background transitions. Explicit retry rebuilds the session and requires station placement again while preserving accepted virtual mission actions.
- Keep the portrait instruction area at a stable height so feedback cannot resize the scene and cancel a gesture.
- Surface stale-frame timing uses receipt time after `Session.update()`. Freshness and action tolerances have not been relaxed.
- Recovery controls bypass the discharge gesture handler, so retry cannot be misrecorded as a discharge release. Failed session configuration closes native resources.

## Verification scope

The candidate is not a claim that physical AR is fixed. A preliminary recovery APK installed successfully on the phone, after which USB disconnected. The final 0.5.3 candidate needs installation and physical retesting after reconnection.

The thermal and interrupted-discharge tests use an emulator thermal-status override; the discharge recovery fixture is synthetic and never constitutes camera-learning evidence. Emulator thermal overrides must never be applied to a real phone. No instrumented tests were run on the user's phone.

### Final candidate checks

- Build and 87 JVM tests passed.
- Two native thermal/retry tests passed together in 19.757 seconds, including a touch-driven retry during interrupted SWEEP. That test failed before the touch-handler fix because retry was recorded as an extra release action.
- Hindi 200% portrait/landscape layout check passed in 20.763 seconds; screenshots inspected.
- Camera-permission-denied/recreation check passed in 15.862 seconds; screenshot inspected. No camera actions were awarded.
- The final APK is debug-signed version 0.5.3, code 13. SHA-256: `60fb5deb89eaf9fe1233f083521926c0a098accaa69f48a038b293719c3d363c`.
- Emulator thermal, font, size and density overrides were restored; camera permission restored. This is not an all-green full-flow run, and it is not real-camera verification.

## Open screen-timing failure

The existing complete gas screen flow passed. The fire screen flow repeatedly failed the continuity check; diagnostic callbacks showed approximately 190–330ms gaps during sweeping. Removing pre-sweep screenshots, asynchronous move injection, and a 720x1600 emulator diagnostic run did not resolve the failure. Test-driver experiments were reverted. This remains an open performance/robustness issue; no timing threshold was loosened and no complete fire result was fabricated. Passing older 0.5.2 runs do not override this result.

## Physical acceptance still required

1. Cool-device launch; inspect actual `RoomAR` tracking reason and selected camera configuration.
2. Move the phone gently sideways over a clear, detailed floor. Confirm TRACKING, a green placement ring and an enabled Place station control.
3. Place all three stations. Turn away and back; confirm they remain fixed in the room.
4. Complete fire and gas camera actions, including interrupted tracking and explicit recovery; confirm no unearned actions appear.
5. Measure frame cadence and thermals over a sustained session on representative Android devices. Do not classify a camera feed, screen simulation, or synthetic fixture as physical AR success.

References: [ARCore tracking failures](https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason), [camera configuration](https://developers.google.com/ar/develop/java/camera-configs), [performance](https://developers.google.com/ar/develop/performance), [Android thermal status](https://developer.android.com/reference/android/os/PowerManager#THERMAL_STATUS_CRITICAL).
