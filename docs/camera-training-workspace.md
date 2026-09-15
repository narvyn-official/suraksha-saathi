# Camera training workspace — 0.5.2 candidate

The existing full-procedure path now uses a dedicated layout when camera mode is selected after the safe-area check. The lesson pages’ primary **Start immersive AR mission** button opens the separate [room mission](room-ar-missions.md) experience. Compact, scrollable instructions sit above the camera; numbered target explanations or saved feedback sit below it. The camera uses the remaining window space. The fixed footer always exposes the current primary action: screen fallback during an action, or Continue during saved feedback. Options include screen/text alternatives, explicit repositioning, centre aiming, action-mode selection and saving/returning.

## Placement and interaction are separate

Camera scene touches no longer enqueue surface placement. Placement requires **Place at camera centre** or **Options → Reposition the scene**. After a successful placement, the standalone placement button disappears in the camera workspace; repositioning remains available in Options. This prevents a touch intended for a training target from moving the anchor.

On the five spatial steps, touch and hold a target directly in the camera view. Its local viewport coordinates are unprojected using the displayed model-view-projection matrix. Alternatively, choose centre aiming in Options and hold the aim control while aligning the phone cross with a target. Camera-centre and direct-touch coordinates are deliberately separate. New spatial evidence records `input: touch` or `input: centre-aim`; screen records cannot claim centre-aim input. Older journals with no input field remain unchanged.

The centre hold cancels on release, cancellation, a second pointer or dragging outside the hold control. Target holds also reset on misses, frame gaps, tracking loss and scene/lifecycle revisions. The activity checks learner ownership before saving. Partial gestures are runtime-only and never become completed evidence after recreation.

Saved feedback keeps a camera scene with updated equipment flags and disabled learning actions. Continue remains in the fixed footer. Feedback can be read and advanced without valid camera tracking; a new camera action again requires fresh tracked frames. The underlying procedure remains a draft, non-certifying learning sequence.

## Recovery

The workspace reports ARCore's actionable tracking failures: more light/camera access, slower motion, a more detailed surface, camera use by another app, or a retry after an internal interruption. These messages follow the [ARCore tracking-failure reference](https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason). They do not diagnose an industrial hazard or measure environmental safety.

Permission-off states keep an Enable camera control and a fixed screen fallback. Header and feedback containers scroll independently at large text sizes. A reparenting defect found during native testing was fixed by clearing the lesson layout's old margins before attaching content to the camera scroll containers.

## Validation boundary

The 0.5.1 release was held as a draft after user feedback that AR targeting/learning was inadequate. The 0.5.2 work addresses a concrete touch/reposition conflict and the small camera panel, but it is not proof of best-in-class AR or a complete vocational simulator. Physical camera placement, visible target alignment, sustained interaction and tracking recovery must be observed on the connected phone. The five discrete holds in this older procedure path still do not measure a continuous extinguisher sweep (the separate room mission now measures a virtual trajectory), real equipment mechanics, real worker positions or practical competence. See [validation](validation.md) and [device protocol](ar-device-validation.md).
