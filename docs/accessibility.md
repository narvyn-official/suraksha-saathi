# Equipment and review accessibility

App 0.4.3 improves a bounded set of Android views: equipment inspection, component practice and delayed decision review. This is not a whole-app accessibility certification or a report of worker usability testing.

## Reproduced problem and changes

On the API 36 emulator with the system font setting at 200%, the previous equipment page retained its model in portrait but collapsed the model viewport to **0 pixels in landscape**. Its final actions were also cut off below the window. The fixed page scrolls as a whole, preserves a 320 dp model viewport and allows controls to grow with text. Rotation, zoom and inspection remain native buttons; tilt now has equivalent buttons. A polite live text readout reports rotation, tilt and zoom after a control action or completed/cancelled gesture. View state survives recreation.

Component practice offers its description alternative before the model. Description answers keep their existing separate attribution; hidden visual marker labels do not gain part names that would reveal the answer. Examples and saved-answer feedback can advance while the camera is unavailable or the model has scrolled out of view. These actions do not create an answer. Actual camera choices still require the existing freshness/tracking gate. Foreground state is required for stage advancement.

The scoped screens now mark real headings and expose pane titles for review/stage changes. Review buttons include the module and decision in their accessible label so repeated cards have distinguishable actions. No screen reader is forced into visual mode or automatically treated as evidence of disability or competence.

## What the tests establish

See [validation](validation.md) for final counts, environment, build checksum and recording details. Native instrumentation uses Android's service-facing accessibility tree and show/scroll/focus/click actions. It checks 200% system text, button dimensions and untruncated text, heading semantics, distinct review labels, description practice, preserved assessment records, and saved-feedback recovery without camera permission. Equipment tests cover portrait/landscape, native controls, cancelled gestures, activity recreation and actual rendered-buffer pixels.

These tests exercise the API that accessibility services use. They do **not** run a human TalkBack listening session, establish a satisfactory spoken reading order, or prove Switch Access/Voice Access usability. The emulator has Android's newer nonlinear font scaling; 200% is the system setting, not a promise that each text size literally doubles. Screenshots show scrollable content, so not every paragraph is simultaneously visible.

## Remaining acceptance checks

- Observe TalkBack speech, focus order, heading navigation, interruptions and Hindi pronunciation with suitable installed voices. Check Switch Access/Voice Access separately, with representative workers where feasible.
- Add a native action for attempting camera placement. Current coordinate-tap placement remains a barrier; accessible screen/text alternatives are available. Never bypass tracked-plane or fresh-image checks merely to expose that action.
- Extend the audit to scored assessments, credentials, onboarding and admin workflows; test additional display sizes, Android 10 devices and landscape combinations.
- Test real device gestures, model presence, rendering/performance, and actual camera tracking. Font and accessibility API checks do not validate physical AR.

## Primary references

- [Android accessibility principles for Views](https://developer.android.com/guide/topics/ui/accessibility/views/principles-views) describes useful labels, distinguishable collection actions, heading/pane semantics and alternatives to gestures.
- [Android 14 font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling) describes support for the 200% system setting and nonlinear scaling.
- [Android accessibility testing](https://developer.android.com/guide/topics/ui/accessibility/testing) distinguishes automated checks from assistive-service and user testing.

References checked 15 September 2026. They inform these implementation choices; they do not endorse or certify this app.
