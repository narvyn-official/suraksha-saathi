# Spatial procedure practice — 0.5.1 pilot

Five existing procedure steps now offer spatial target selection: fire aim, left/right/return target positions, and gas attendant positioning. Other steps retain their ordered action controls. Both safe and unsafe choices have neutral target rings. Spatial selection is the default on supported steps; the worker can explicitly switch to button or text actions.

In screen practice, touch and hold a ring, moving the finger to correct alignment. In camera practice, place the tabletop scene, aim the phone's centre cross at a target, then hold the native aiming control. Only the phone moves; the app does not require the worker to walk into any area. Gas targets designate positions for a miniature attendant, not actual safe locations. All placement and targeting must happen in a cleared training area.

## What is measured

The app unprojects the screen pointer or camera centre into a ray in the anchored model's coordinates and selects the nearest intersected virtual target sphere. A continuous hold needs at least 650 milliseconds of eligible samples, with no sample gap over 150 milliseconds. The record contains relative sample times and the ray's perpendicular distance from the target centre divided by its radius. These geometry tolerances are interaction settings, not recommended fire distances, exclusion distances or ergonomic standards.

A target miss, target change, release, interruption, mode/viewport revision or invalid sample discards the incomplete hold. Camera samples require tracking, anchor placement, fresh camera images and visible targets. Repeated camera-image timestamps cannot advance a hold. Screen practice samples the last displayed static geometry on the UI thread, without continuously redrawing the equipment or borrowing camera credit. The pointer must stay active and the displayed scene revision and visibility must remain valid.

The numbered rings approximate the projection of spherical targets. They are symbolic interaction overlays, not mesh collision, real-object recognition or occlusion tests.

## Evidence and compatibility

Completed holds attach a versioned `spatial` object to the existing procedure action event. It identifies `target-hold`, the action and bounded sample data. Replay rejects unsupported steps, action mismatches, text-mode attribution, invalid errors, non-increasing times, gaps and insufficient duration. Older procedure journals replay unchanged and receive no spatial credit. Changing a session still saves before advancing the interface, with learner ownership checked by the host activity.

Screen and camera attribution remain explicit. The result shows spatial-hold counts alongside the existing action-mode counts. These are client-generated practice records, not cryptographic sensor attestation. Procedure journals remain on the phone and are not currently trainer-import or credential evidence.

## What this does not establish

The four fire targets train virtual location and order; they do not measure a continuous sweep trajectory, nozzle mechanics, grip force, real extinguishing performance or an actual retreat route. The gas interaction selects the miniature attendant position; it does not track a real buddy or measure the atmosphere. Completion is non-certifiable and does not establish practical competence.

Positive camera behaviour, frame performance, scale, lighting and tracking recovery need physical ARCore device testing. Screen/emulator tests cannot substitute for that evidence. The content and Hindi wording remain draft pending competent and native-language review. See [validation](validation.md) for actual checks and [device test protocol](ar-device-validation.md) for the next gate.
