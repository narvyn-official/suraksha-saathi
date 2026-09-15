# Room AR missions — 0.5.2 candidate

Open a fire or gas lesson and choose **Start immersive AR mission**. The camera becomes the training workspace. The user explicitly places three separate floor anchors: equipment, a simulated hazard, and a green withdrawal/outside point. Objects use metre-based geometry. Turning the phone changes the viewing direction; the exercise does not require walking. These are user-marked training stations, not detected equipment, real exits or surveyed safety distances.

## Actions and consequences

| Mission | Learner action | Scene response / recorded evidence |
|---|---|---|
| Fire | Touch the alarm call point; drag the retaining pin outwards | Pin disappears; accepted controls enter the mission journal |
| Fire | Align the camera cross at the virtual fire base | A continuous 300 ms alignment advances the mission |
| Fire | Hold discharge and turn the phone across the base | Five adjacent horizontal bands and at least 1.5 seconds of continuous tracked input are required; the discharge cue and flame intensity respond |
| Fire | Release discharge and select the green withdrawal point when conditions worsen | Flames grow after the sweep; the mission records withdrawal, never a claim that the fire was extinguished |
| Confined space | Inspect the explicitly simulated meter | A deliberate hold-and-release advances the outside-only exercise; no gas readings are sensed |
| Confined space | Drag tape between the barrier posts | Open posts become a deployed exclusion barrier in front of a simulated access hatch |
| Confined space | Drag from the equipment marker to the green outside point | A full-height illustrated attendant appears outside |
| Confined space | Select the outside point to refuse entry and request simulated support | The mission ends outside because rescue readiness remains unconfirmed; no call is actually placed |

Timing, alignment bands, gesture distances and placement spacing are interaction settings. They are not operating standards, safe firefighting distances, gas limits or a practical equipment assessment. The fire scenario assumes an authorised role, suitable extinguisher and clear retreat path. The interface explains those assumptions before practice.

## What changed in the learning experience

The mission is an enacted sequence with scene consequences. There are no multiple-choice answers in this path. Short prompts identify the next action, world-positioned rings guide attention, and haptic feedback marks completed actions. The debrief shows accepted actions and measured virtual alignment/sweep samples. The sequence rehearses a change of plan when conditions worsen, rather than rewarding continued discharge.

This implements guided practice, cues next to the relevant object, immediate feedback and a response to changing conditions. It does not establish improved retention or transfer to a worksite. Existing recognition, decision assessment and scheduled recall features remain separate; these new mission journals do not automatically feed those scores or award certificates. Worker studies, unprompted transfer tasks and safety/language review remain necessary. See [learning evidence and limits](ar-learning-evidence.md).

## Input and recovery boundaries

- Camera practice uses ARCore horizontal-plane anchors, current camera/view matrices and real image timestamps. Repeated images do not renew their age. Alignment/sweeps reset on invalid alignment, excessive sample gaps or tracking loss. Pausing, modals, focus loss and geometry changes cancel unfinished gestures. A resize/cancellation cannot stand in for an intentional release.
- Placement is a separate control. Touching an equipment target does not move the anchors. Placement commits are guarded by the active scene revision and a fresh tracked image.
- Camera rendering requests are bounded to approximately 30 per second to avoid an unbounded render loop competing with touch input. This is a scheduling limit, not a measured device frame-rate guarantee.
- The explicitly labelled screen mission uses the same engine and its displayed static scene geometry, with actual touch samples on the UI thread. It never receives camera attribution. Screen aim requires a finger down; a short released tap cannot accumulate later alignment.
- Options provide a new screen mission, the text procedure alternative, saved mission summaries, restart and exit. Switching camera/screen starts a separate attempt so its presentation cannot silently change.
- Scene graphics are original illustrative meshes and material lighting. No photorealism, real-world occlusion, hand tracking, nozzle mechanics, live fire/gas detection or worker-body positioning is claimed.

## Local records

`room-missions.db` stores worker-scoped snapshots separately from assessments and credentials. The host copies engine state, applies a completed control, saves, then replaces active state. Failed persistence pauses actions without advancing the accepted record. Owner, module and camera/screen mode cannot be rebound under the same ID. Result data is explicitly non-certifiable.

Activity recreation retains accepted runtime progress while resetting incomplete gestures and requiring camera stations to be placed again. Process death starts a new attempt; the previous audit remains available under saved missions. There is no trusted import/replay or trainer export of these journals yet.

## Validation status

See [validation.md](validation.md) for actual test results. Physical three-station placement, world stability while panning, aim/sweep accuracy, tracking recovery and sustained performance on the target phone still require observation. The Samsung phone used for the earlier installation check disconnected before this candidate was ready. Emulator screen missions do not verify camera AR.
