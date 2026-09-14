# Staged equipment-recognition practice

Android app 0.3.1 adds a camera-free learning interaction using the same original meshes as AR placement. Curriculum remains 0.3.0; the component catalogue has its own version (1). The exercises identify 12 visible components across fire, gas, machinery and PPE illustrations. They do not add assessment questions or establish operational competence.

## Worker flow

Open a lesson → **Explore in 3D** → **Practice finding parts**.

1. On first use, an example names a part, highlights its callout and explains a distinguishing cue.
2. Names disappear. The learner follows a line to a component and selects its letter.
3. The model changes between two fixed orientations (20° and −20°). Letters are reassigned and the learner identifies the part again.
4. Feedback explains the cue and invites the learner to describe their reasoning. An incorrect or uncertain response reveals help and requires a guided retry; it cannot be rewritten as an unaided correct answer.
5. After three parts, the exercise returns through **Review decisions over time**. Later review rounds begin without examples. Help remains available without a time penalty.

Callouts have separate 48dp touch areas and leader lines ending at source-derived model anchors. They are recognition markers, not geometric mesh hit testing. Angles are restricted to keep selected components on the visible front of these generic illustrations. No arbitrary orbit, real-world occlusion or spatial competence score is claimed for this exercise.

## Text alternative and attribution

**Use text descriptions** hides the model and offers descriptive choices. This can be completed in English or Hindi using ordinary Android buttons. A requested hint reveals the matching cue. This alternative exercises verbal recognition; its changed stage reorders choices rather than claiming a visual-angle test.

Records distinguish visual-marker, description and mixed responses. Once descriptions have been exposed during a decision, switching back to the model cannot count that answer as purely visual. Hints and wrong/uncertain attempts also remain recorded. Example-stage instruction is separate from retrieval attempts. Screen-reader interaction, text scaling and comprehension still need representative user testing; ordinary accessible controls alone do not establish accessibility validation.

## Local persistence and spacing

Every transition and answer saves immediately in the separate SQLite `component_learning` table. Activity recreation and reopening resume the current part, stage, feedback and assistance. Database version 3 adds the table without rewriting prior attempts, credentials or decision-review records.

The current round records at most 200 recent responses plus summary counters; starting another round replaces the prior detailed round. These are local learning aids, not an immutable practical-assessment audit. Nothing enters assessment exports or credential issuance.

A clean first round returns after one day. Clean reviews that start when due progress through pilot intervals of 3, 7 and 14 days. A missed decision or requested hint brings the exercise back after 10 minutes and resets the spacing streak. Clean early practice preserves the already scheduled due date and streak; repeating immediately cannot postpone a due review. Description-based recognition is recorded separately but uses the same scheduling heuristic. Device time controls the queue, and no notification service runs.

## Research and remaining scope

This implements the initial guided-identification and fading-label subset from [the learning evidence report](ar-learning-evidence.md). Spacing and retrieval are design foundations, not measured retention results for this application. The future field study must test delayed identification on different approved equipment and safe practical decisions with a competent assessor.

The component texts describe the existing generic meshes and reuse the pilot content's equipment limitations. They do not introduce real equipment operating instructions. Hindi and safety content remain drafts pending competent review. Camera-based component practice, authored photoreal assets, a physical ARCore test and the fifth proposed emergency-response domain remain outstanding.
