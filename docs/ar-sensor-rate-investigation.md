# Physical AR sensor-rate investigation — 16 September 2026

## Reproduced fault

On the connected Samsung SM-S921B (Android 16 / API 36), both Suraksha Saathi
and Google's Hello AR Java sample failed to establish usable surface placement.
The failure persisted after a confirmed full reboot. Google Play Services for AR
was version 1.56.262080393; both clients used ARCore SDK 1.56.0.

The reference sample came from Google SDK tag `v1.56.0`, commit
`3abfeb18669c2cbb2d07057f135d117ee9d91826`. Build-only adaptations used AGP
9.0.1, compile/target SDK 36 and the optimized ProGuard configuration so it
could build with the available JDK. No reference AR runtime source was changed.

While the reference camera was foreground, `dumpsys sensorservice` reported
**160 ms** sampling periods for both uncalibrated accelerometer and gyroscope
(6.25 Hz). Sensor event timestamps also showed approximately 160 ms intervals.
ARCore repeatedly logged `Failed to query IMU integrated pose`, `timestamp ...
is too new`, and empty or single-sample IMU buffers.

## Controlled manifest change

Adding only `android.permission.HIGH_SAMPLING_RATE_SENSORS` to the already-built
reference sample's manifest, then reinstalling and reopening it, changed both
active sensor periods to **5 ms (200 Hz)**. Initial enable entries in the sensor
registration history still said 160 ms; the **current selected period** was 5 ms.
These are different measurements and must not be confused.

The same permission is now included in Suraksha Saathi's main manifest. The
candidate APK was built and installed over the existing app without clearing
data. Android reports the permission as granted. An instrumentation regression
check verifies the permission in the installed, merged APK on API 31+.

Android documents sensor-rate restrictions and this manifest permission in its
[sensor overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview#sensors-rate-limiting).
The observed 6.25 Hz fallback is evidence from this device/runtime combination,
not a claim that Android's documented general limit is 6.25 Hz. The exact
internal ARCore/OS failure path has not been established.

## Verification boundaries at the sensor-fix stage

- Build succeeds; 149 JVM tests pass, with no failures or skips.
- Installed manifest permission and placement-overlay regressions: 2 emulator
  tests pass. The overlay fixture was visually inspected; it is synthetic UI
  evidence, not physical placement evidence.
- Reference sample sensor-rate recovery is confirmed on the physical phone.
- Suraksha Saathi also ran both uncalibrated sensors at 5 ms (200 Hz).
  A captured set of 27 five-second reporting intervals contained 2,668 distinct
  camera frames, of which 2,649 reported `TRACKING`; the initial interval
  included startup. Subsequent intervals were fully tracked. No anchor-placement
  event was recorded in this capture. Thermal status was 3, so the app's existing
  frame pump reduced rendering to approximately 20 fps.
- Stable object placement and movement in Suraksha Saathi still require physical
  verification; the sensor-rate change alone is not an AR acceptance test.
- The operator confirmed tracking worked but equipment could not be placed.
  Added five-second placement diagnostics distinguished floor detection from
  hit, range, footprint, spacing and dwell failures. Initially no floor plane
  existed; later a tracked upward-facing floor appeared (`floors=1`). The centre
  hit was outside it (`floorHit=false`). The UI now outlines a detected floor
  even when the crosshair misses it and gives an explicit aiming instruction.
  A physical screenshot showed the real plane outline at the lower left of the
  view. No placement acceptance guard was relaxed.
- The phone reached critical thermal status during the extended investigation.
  The app paused the camera and could retry after it cooled below that threshold.
  Thermal protection has not been bypassed.
  The final check again reached thermal status 4 before placement could be
  verified; the next physical test must start after cooling.

## Physical acceptance sequence

1. Open Fire safety → Start training in a clear practice area after the phone cools.
2. Check current sensor periods and fresh `TrainingAR` / `RoomAR` messages.
3. Scan a textured floor sideways until a complete placement preview appears.
4. Place a station, then change viewing angle. Confirm the station remains at
   its physical location and its ARCore anchor remains tracking.
5. Briefly background the app, return, and verify recovery without duplicate
   camera owners or credit from stale frames.
6. Repeat placement for the gas scenario. Do not treat these checks as proof of
   completed training or practical safety competence.

Raw camera screenshots and phone identifiers remain in ignored local diagnostic
files; they are not part of the public evidence.

## Subsequent placement and UI checks

The sensor-stage limitations above are retained as the investigation history.
Later checks confirmed three anchors and user-observed stability, followed by
completed fire-evacuation and outside-only gas camera practice. The full-footprint
placement condition was replaced with a mapped anchor-patch requirement plus a
visible full-model warning; freshness, range, tracking and spacing remain gated.
The fixed-screen candidate subsequently placed two anchors and blocked overlapping
footprints. The user confirmed automatic Hindi narration. See the
[current release evidence](learning-update-2026-09-16.md) for exact verification
boundaries, including the unverified fire discharge branch.
