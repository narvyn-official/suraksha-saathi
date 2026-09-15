# Native camera placement — app 0.4.4

Both AR decision stations and fire component practice now expose a labeled native **Place at centre** action. Aim the small centre cross at a clear training surface and activate the button. Direct camera taps remain available. The two inputs use the same screen-local request and standard tracked-plane hit test; neither input supplies an assessment answer.

## Presence and interaction

The running camera stays visible before placement and during tracking interruptions. The light paused-camera panel appears when the camera is stopped. A failed hit reports that no tracked surface was found and preserves an existing anchor; a successful replacement is created before the old anchor is detached. The component screen's separate **Place model again** reset still clears its anchor without clearing learning progress.

Component practice reserves a measured button strip outside its camera surface, so the cross, hit-test coordinates and component projections use the same remaining viewport. Decision stations use a scrollable page with a 320 dp camera viewport, growing native controls and a persistent screen alternative. Scroll until the camera centre is visible before placing. Target-size checks do not establish that every physical camera layout is usable.

This improves the placement interaction around the existing material-lit equipment. Meshes, proportions and shaders are unchanged. The cross indicates the requested screen point, not a detected surface, real hazard, instrument reading or guaranteed placement. No real-world occlusion, physical resistance or photorealism is claimed.

## Request and lifecycle rules

- Each request captures the current revision, viewport dimensions, interior coordinates and monotonic creation time. Invalid dimensions, boundary/outside coordinates, NaN and infinity are rejected.
- Consumption requires the current revision and dimensions, an active camera flow, tracking and a fresh camera image. Requests expire after 500 ms. Existing image freshness rules still reject stale and cached pre-pause images.
- A standard hit must identify a tracking, upward-facing horizontal plane with the hit pose inside its polygon. Instant placement and invented fallback distances are not used.
- A shared compare-and-set queue consumes a matching request once. An older frame cannot consume or discard a newer revision's request. Lifecycle cleanup clears pending work; pause disables camera eligibility before waiting for GL shutdown.
- Placement invalidates pending choices. New answers still require their independent tracking, visibility, revision and persistence checks. Placement itself creates no answer, learning event or camera-practice credit.

## Evidence and limits

Nine new JVM cases exercise immutable request validation, expiry, viewport/lifecycle changes, concurrent consumption and older-frame rejection. Native tests activate the real service-facing button actions in English/Hindi at normal and 200% system font, deny camera permission, recreate activities, check hidden answers and unchanged records, and complete the screen/description alternative. See [validation](validation.md) for exact results and recording details.

Positive hit quality, stable scale/presence, replacement after a miss, actual camera interruptions, small-screen/landscape placement, TalkBack speech, lighting and frame-time need physical-device validation. These emulator checks do not establish practical skill or learning gains.

## Primary references

- [ARCore Frame API](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame) specifies camera-view pixel coordinates for standard hit tests and distinguishes instant placement.
- [Android accessibility principles for Views](https://developer.android.com/guide/topics/ui/accessibility/views/principles-views) describes meaningful labels and accessible alternatives to gesture actions.

References checked 15 September 2026. The request lifetime and interaction rules are this app's design choices.
