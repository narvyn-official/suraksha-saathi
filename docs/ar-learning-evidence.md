# Evidence-informed AR learning design

Research and design notes, 14 September 2026. See [current offline review implementation](spaced-review.md) and [staged component practice](component-practice.md) for the implemented subsets; the six interaction designs below include work still to build. These are proposed learning interactions and acceptance criteria, not a report of implemented features or demonstrated learning gains. The studies below do not establish effectiveness for Suraksha Saathi or for Jharkhand workers; that requires local evaluation.

The recommended foundation is **realistic equipment, meaningful decisions, progressively reduced help, corrective practice, and delayed recall**. Visual presence can help recognition and engagement. A convincing model alone does not establish learning, practical competence, or safe transfer.

## What the evidence supports

| Principle | Evidence and important limits | Product implication |
| --- | --- | --- |
| Retrieval practice | Roediger and Karpicke's experiments found an advantage for retrieving studied material on delayed tests, even when repeated study looked better immediately. These were prose-memory tasks, not industrial skills. [Original study, 2006](https://www.psychologicalscience.org/journals/psychological-science/j.1467-9280.2006.01693.x/) | Ask the learner to identify a component or decide the next safe action before revealing the answer. Measure delayed performance separately from same-session success. |
| Spacing | Cepeda and colleagues' quantitative synthesis found that useful spacing depends on the intended retention interval. It primarily concerned verbal recall; it does not supply a universal schedule for machinery skills. [Authors' institutional repository, 2006](https://digitalcommons.usf.edu/psy_facpub/1771/) | Revisit decisions after a delay. Treat any initial reminder schedule as a tunable product hypothesis, not a scientifically optimal formula. |
| Feedback and learning from errors | A meta-analysis found benefits of error-management training, particularly for transfer, with substantial variation between studies. Active exploration was part of the intervention. [Keith and Frese, 2008](https://pubmed.ncbi.nlm.nih.gov/18211135/). A research review emphasizes corrective feedback and examining the reasoning behind an error. [Metcalfe, 2017](https://www.columbia.edu/cu/psychology/metcalfe/PDFs/Learning%20from%20errorsAnnual%20ReviewMetcalfe2016.pdf) | Permit reversible mistakes in clearly marked simulated practice, then explain the missed cue and rehearse the correct response. Never encourage a real unsafe act. Preserve strict assessment rules. |
| Guidance matched to experience | In an instructional experiment, the relative benefit of worked examples changed as learners gained experience. [Kalyuga, Chandler and Sweller, 2001](https://www.tandfonline.com/doi/abs/10.1080/01443410124681). Another experiment found a worked-example benefit without an expertise reversal in less structured legal reasoning, demonstrating that fading is task-dependent. [Nievelstein and colleagues, 2013](https://www.sciencedirect.com/science/article/pii/S0361476X12000677) | Begin with a short demonstrated decision, move to completion with an optional hint, then attempt independently. Base progression on performance rather than age, language or background. |
| Spatial assistance is distinct from learning | A small within-subject study with professional mechanics found quicker task localization using tracked head-worn AR. It evaluated assisted maintenance performance, not delayed unaided safety competence. [Henderson and Feiner, 2009](https://www.cs.columbia.edu/graphics/projects/armar/pubs/henderson_feiner_ismar2009.pdf) | Put labels beside the relevant part during explanation. Remove them during independent identification; test whether learners can find the part on unfamiliar real equipment later. |
| AR does not automatically reduce effort or improve learning | A randomized maintenance study using HoloLens reported shorter maintenance times alongside greater mental workload. This is not proof of improved long-term retention. [Original study, 2023](https://pubmed.ncbi.nlm.nih.gov/37765755/). A small mixed-reality manufacturing study found no significant difference in tested knowledge or physical-model task scores; physical-task equivalence was also not established. [Gonzalez-Franco and colleagues, 2017](https://www.frontiersin.org/journals/robotics-and-ai/articles/10.3389/frobt.2017.00003/full) | Use one relevant prompt at a time, offer a camera-free equivalent, and measure workload and transfer. Do not substitute engagement scores for learning outcomes. |
| Physical experience and fidelity | Experiments with physical angular momentum supported a benefit of task-relevant bodily experience, not arbitrary gestures. [Kontra and colleagues, 2015](https://www.psychologicalscience.org/journals/psychological-science/0956797615569355/). A randomized assembly study found complementary advantages for physical and cognitive fidelity. [Hochmitz and Yuviler-Gavish, 2011](https://pubmed.ncbi.nlm.nih.gov/22046722/) | Reproduce component relationships and action consequences accurately. A phone cannot reproduce equipment weight, grip resistance, glove dexterity or respirator fit; those require supervised physical practice. |
| Psychological safety | A manufacturing-team field study associated team psychological safety with learning behavior. This is observational evidence about teams, not evidence that an app can create a safe workplace culture. [Edmondson, 1999](https://journals.sagepub.com/doi/pdf/10.2307/2666999) | Make asking for help and stopping unsafe work legitimate learning actions. Use respectful feedback, private practice and trainer discussion rather than public rankings or humiliation. |

The evidence base is strongest for general learning principles. Much AR research uses headsets, students, small samples, or immediate assisted tasks. Transfer to handheld Android, regional-language delivery and industrial onboarding remains an empirical question. Proposed interactions below are original product applications of these principles, not published, validated intervention packages.

## Six concrete interactions

### 1. Find it, explain it, find it again

The worker examines a realistically proportioned extinguisher, gas detector or machine enclosure. An initial guided inspection highlights one relevant part with a short explanation and optional narration. Next, labels disappear and the learner selects the requested part. A follow-up asks why it matters, with pictorial or short-text choices. A later attempt changes the model orientation and the part's screen position.

The purpose is to retrieve meaning and recognition, not memorize where a glowing button was placed. Application of retrieval practice and spatial-assistance findings above; effectiveness of this exact interaction is untested.

**Acceptance criteria:** every scored component has an explicit model-space target and equivalent accessible list control; no answer-colored highlight before an independent response; record target ID, first response, help used and scenario version. Labels can be replayed in practice. A hint-supported response never counts as independent mastery. Supported camera and non-camera modes use the same rubric. Reject taps while tracking is unstable, without recording a learner error.

### 2. Short return visits with a changed problem

After a session, offer a two- or three-decision refresher focused on previously missed safety principles. Start a pilot with return visits around days 1, 3, 7 and 21; these dates are a configurable hypothesis, not a universal optimum. Change the object position, wording or surface appearance while preserving the reviewed safety rule. Mix in previously successful decisions so that the schedule does not teach only the learner's last mistake.

This applies spacing, combined with retrieval. Butler's prose-learning experiments also found transfer benefits on new inferential questions, but this does not establish transfer to physical emergency behavior. [Original transfer experiments, 2010](https://pubmed.ncbi.nlm.nih.gov/20804289/)

**Acceptance criteria:** schedule and progress survive offline use, restart and delayed sync; store the rule and curriculum version, not only a question number; late practice is welcome and never produces a punitive streak reset. Success on an old item must not override a newer critical assessment failure. Report delayed independent accuracy separately from completion count. Safety reviewers approve every substantive scenario variant; unrestricted generative scenarios cannot enter certification.

### 3. Predict, safely observe, repair the decision

Before a simulated action, ask what the learner expects to happen. For example, a stationary virtual machine may still show an explicitly illustrative stored-energy source. After the choice, pause the scenario and explain why visible stillness did not establish safe isolation. Let the learner replay the same decision with the correct rationale, then return later with a different example.

Use a calm annotated state change rather than graphic injury, a loud surprise or a reward for causing an accident. This is a design application of corrective error learning, not a realistic physics prediction engine.

**Acceptance criteria:** the application labels all simulated instrument values and hazard overlays; it never represents camera imagery as a gas or energy measurement. Feedback identifies the observed cue, the consequence of the decision, and the safe next step appropriate to the worker's authorization. Critical mistakes stop scored assessment under its existing rules. Practice replay creates learning evidence, not a retroactively corrected assessment pass. Animation can be paused or disabled.

### 4. A coach that gradually steps back

Use three visible stages: **See an example → Try with help → Try independently**. In the example, a brief explanation points to the relevant real-looking part. In coached practice, show the task first and provide help only when requested. In the independent stage, remove the coach and later introduce one approved variation. A failed independent attempt offers the appropriate explanation again.

This adapts worked examples and progressive guidance. The exact promotion rule is a testable design choice, not a diagnosis of intelligence or a fixed learner type.

**Acceptance criteria:** every decision exposes assistance level; users can revisit examples without losing prior evidence; the coach never reveals an assessment answer. The interface never requires listening and reading competing long passages while moving the camera. Display one action prompt at a time, with nearby optional detail. Progression considers repeated independent safe responses, not time spent tapping Next. Do not add deadlines until a validated task requires them.

### 5. Rebuild the safe plan in a different layout

On a safe training surface, place virtual zones, an exit and an assembly point. Ask the worker to identify the safe destination and order the next actions. In a later scenario, change the layout and introduce a reviewed obstruction, requiring a new decision using the same principle. Include a stationary tabletop mode and large step-selection controls; walking around is optional.

This targets relational understanding and transfer rather than a memorized route. It draws on spatial practice and variable application, but is not validated real-site navigation. The app must not guide a worker through an active emergency using arbitrary virtual anchors.

**Acceptance criteria:** all spatial tasks work within a cleared training area and have a stationary equivalent. A visible training designation remains throughout. Site-specific routes require trainer approval and versioning. The rubric accepts all reviewed safe sequences, not one arbitrary animation order. Test with a changed physical training layout without AR cues before making a transfer claim. Never infer physical competence from touch coordinates alone.

### 6. Buddy handover and respectful challenge

Two learners use one device and take turns explaining a decision: one names the cue and intended action; the other chooses whether to confirm, request clarification or stop and escalate. A solo alternative offers recorded, reviewed examples. One scenario can have a confident simulated colleague suggest skipping a check; the learner practices asking for the missing evidence and choosing a safe escalation route.

The aim is to rehearse communication and uncertainty, with psychological safety as a design consideration. This is not a claim that paired use alone changes workplace reporting culture.

**Acceptance criteria:** roles and instructions are explicit; stopping to ask for help is never penalized for speed. Do not use voice recognition to grade language, accent or confidence. Narration is optional, with equivalent visual content. Individual assessment remains separate from shared practice. Trainer observations use a reviewed rubric, assessor identity and date; they cannot be created by the app merely because a buddy button was pressed.

## Make equipment realism educationally useful

For each asset, review four things independently:

1. **Recognition:** plausible dimensions, silhouette, control placement, legible markings and material appearance. Use an identified generic training model or an authorized manufacturer-specific asset; do not invent a manufacturer's safety markings.
2. **Function:** actions have accurate prerequisites and state transitions. Distinguish a visible stop control from an isolation device. Never imply that every extinguisher type has identical controls or indicators.
3. **Presence:** stable placement, believable scale, consistent lighting and contact with the training surface. If tracking is lost, pause interaction and explain rather than let an object jump unpredictably.
4. **Transfer boundary:** expose limitations plainly. Touching a rendered part rehearses identification and decision-making; it does not prove manual operation, lifting ability, fit testing or authorized maintenance competence.

These are proposed design requirements. More visual detail is not by itself a learning intervention. A randomized clinical simulation study found comparable learning with lower physical model fidelity within a structured curriculum; that finding cautions against assuming a universal benefit from realism but cannot be generalized directly to mining. [Original fidelity study, 2015](https://pubmed.ncbi.nlm.nih.gov/26536341/)

## Accessibility and language requirements

Use workers' actual language and accessibility preferences, not assumptions based on tribal identity, education or age. Preserve Hindi and Santali as independently reviewable content packs. Until native-language and safety review is complete, mark a pack as a draft or unavailable rather than supplying fabricated translations.

Provide short sentences, large targets, replayable offline narration where an approved voice asset exists, captions, meaningful icons with text labels, and stationary interaction. Test comprehension of the same safety concept across language versions. Allow text, picture selection and spoken explanation to a trainer without assuming that automatic speech transcription is valid assessment. Keep optional confidence self-reports private and separate from scores; they are not measures of personality, mental health or employability.

## First two implementation priorities

**First: a staged component-inspection loop (interactions 1 and 4).** It gives the improved equipment models a clear instructional purpose immediately, works offline and in camera-free mode, and creates an explicit distinction between assisted practice and independent evidence. Start with a small set of reviewed components per module before adding many decorative assets.

**Second: versioned spaced refresher sessions (interaction 2).** This addresses retention directly and can work on every supported phone. Establish correct scheduling, offline persistence and delayed-outcome reporting before adding notifications or claiming an adaptive learning algorithm.

Then add error replay, spatial variants and buddy practice once their safety states and rubrics are reviewed. Prefer these explainable techniques to inferred emotions, fabricated learning-style classifications, constant reward animations, or a leaderboard that pressures workers to rush.

## Evaluation that can establish a stronger base

**Formative stage.** Run observed sessions with a small, purposively varied group of workers and trainers across sectors, phone familiarity, language preferences and access needs. This identifies comprehension and usability failures; it cannot establish efficacy. Use inactive equipment and controlled training spaces. Record misleading affordances, tracking failures, accidental taps, confusion between simulated and real readings, and barriers to asking for help. Obtain appropriate consent and do not use participation as employment assessment.

**Comparative stage.** Pre-register outcomes and analysis before recruitment. Compare the existing 2D learning flow with the improved learning loop, matching safety content, exposure time, language and feedback opportunity. If resources permit, use a factorial design that separates the AR display from the learning strategy: 2D/current, AR/current, 2D/structured practice, AR/structured practice. Otherwise, explicitly report that an observed benefit could arise from the combined package rather than AR alone. Randomize individuals where feasible; use cohort randomization with appropriate analysis if participants train together and contaminate conditions. Stratify important baseline factors without treating demographic groups as inherent ability categories.

**Primary outcome.** Independently observed safety-critical decisions on an unfamiliar but equivalent inactive-equipment scenario at a delayed assessment, such as day 7. A competent safety trainer defines the rubric in advance. Assessors should be unaware of training assignment where practical. A phone score is a separate secondary outcome. All unsafe simulated choices are intercepted; no live hazard is introduced.

**Secondary outcomes.** Assess day-30 retention if feasible; first-attempt critical errors; correct escalation decisions; explanation quality under a reviewed rubric; assistance needed; confidence calibration; workload and usability; camera tracking failures; completion/withdrawal; and time only alongside correctness. Satisfaction and visual presence may explain acceptance but cannot stand in for competence. Use equivalent unseen questions to limit rehearsal of an answer key.

**Sample and analysis.** Estimate sample size from a pre-agreed meaningful difference, pilot variability, clustering and expected missed follow-ups; do not announce an arbitrary powered sample. Report all allocated participants, missing-data handling, effect estimates and uncertainty. Examine accessibility/language disparities descriptively unless subgroup comparisons are adequately powered. Investigate every observed dangerous misconception before broader use. Do not infer a reduction in fatalities from a short, small training study.

**Release gate.** Competent safety and language review, physical-device tracking/performance checks, accessible fallback completion, and no known misleading critical feedback are prerequisites for a field pilot. A learning-effectiveness claim requires the comparative evidence above. Production practical certification additionally requires an appropriate governed assessment process; neither this document nor a cryptographic QR signature supplies that authority.
