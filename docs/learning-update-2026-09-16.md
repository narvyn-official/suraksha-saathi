# Learning update — 16 September 2026

This Android candidate adds fixed screens, automatic spoken learning, detailed fire-response comparisons, personal decision review and repeat action practice to the existing fire and confined-space missions. It also makes camera placement selectable by tapping a detected floor patch. It is included in the debug-signed v0.7.1 pilot prerelease.

## Learner flow

1. **Prepare:** a short briefing and three setup cards explain surface detection, placement and the three-station layout. Opening the setup guide does not start training.
2. **Do and explain:** guided mode's **Coach** describes the current action, its purpose and a question to answer in the learner's own words. English and Hindi content covers all 17 fire/gas action phases. Opening coaching pauses scene input.
3. **Review personal decisions:** the completed mission shows accepted actions, rejected choices and requested hints from its actual saved history. It identifies up to two actions to revisit and explains corrections. Skipped extinguisher actions on an evacuation path are not counted as failures.
4. **Retrieve:** the review can launch a separate attempt without cues, preserving the completed record. Requested recall hints remain recorded. The detailed guided coach cannot be opened as an unrecorded hint during recall.
5. **Change conditions:** the fire review can start a fresh recall exercise with the explosion-risk setting reversed. The announced scenario changes whether extinguisher use is available. This is one authored variation, not automatic generation of new scenarios.
6. **Return later:** the offline review queue suggests action rehearsal from completed worker-owned records. It separates camera and screen practice, rejects invalid/incomplete records and links back to the matching module and scenario. Assessment and certificate records are not modified.

The scheduling heuristic uses one day after guided practice or a recall attempt with hints/corrections, three days after one clean recall attempt, and seven days after two or more consecutive distinct clean recall attempts. The date is based on the last saved completed practice. These intervals are product choices, not validated mastery thresholds or certification deadlines. Consecutive attempts do not establish that practice was actually spaced in time.

**Automatic instructions are enabled by default.** Each new room setup stage or mission task is narrated once in the selected English/Hindi language. Guided narration includes what to do, what to avoid and why; recall narration reads only the neutral task. Lesson and written-question screens also read their content automatically. Changes of task or language replace the old narration, and leaving the screen stops it. Frame-by-frame tracking updates do not repeatedly interrupt the learner. **Options → Mute automatic instructions** stores the preference; the main language menu exposes the same setting. **Speak instructions** and **Listen / stop** remain available for replay.

Speech uses an installed Android voice for the selected language only when the voice reports that it does not require a network connection. If unavailable, a language-specific notice appears and text remains usable offline; the app does not silently speak another language. There is no speech recording or automatic grading of a learner's explanation. The user confirmed automatic Hindi instructions are audible on the connected Samsung.

Speech-engine discovery uses the manifest query required by the [Android TextToSpeech documentation](https://developer.android.com/reference/android/speech/tts/TextToSpeech). Voices marked as not installed are excluded, and failure to select the offline voice does not fall through to speaking with a previous voice.

## Placement change

A learner can tap a visible patch inside the detected floor outline instead of keeping the centre crosshair over it. The preview follows the selected screen position. Selecting a new point resets the surface dwell and invalidates eligibility from the previous point. A tap never creates an anchor: **Place station** still requires a fresh tracked frame, a mapped 15 cm anchor patch, acceptable range and station spacing. Pausing or resizing clears the selection.

The earlier Samsung sensor-rate fix remains included; see [the investigation](ar-sensor-rate-investigation.md). On 16 September, the connected Samsung accepted three real plane anchors. The user confirmed the models stayed attached while moving and looking away/back. Its saved camera journals show completion of the fire evacuation-only branch (six accepted actions) and the gas outside-only branch (seven accepted actions). No assessment evidence was inserted. The fire discharge/aim/sweep branch still needs a full physical check.

The former full-model plane-containment requirement prevented placement on small mapped patches. The anchor now needs its support patch inside the plane; a full model extending beyond the mapped polygon retains an amber layout warning. This does not assess floor clearance or physical support.

Placement also requires **at least 20 cm between the oriented virtual footprints**, beyond the existing centre-distance and outside-barrier rules. Candidate orientation matches the rendered preview. A conservative polygon separation check prevents virtual equipment/marker overlap; it is not a workplace safety distance. **Placement help** remains usable without a detected plane, explains the latest camera status, and recentres the aim with **Continue scanning** without deleting placed stations. Scanning is automatic; the old disabled “Scan for a surface” pseudo-button is gone. **Place station** is a distinct button enabled only after all placement conditions pass.

## Fire response guide

Twelve paced cards include seven interactive comparisons: ordinary solids, diesel spill, energised electrical equipment, leaking fuel gas, reactive metal, cooking oil and unknown-material/sand use. Each choice explains its consequence; wrong choices can be revisited. Additional cards cover hazard identification, unit inspection, pin/aim, squeeze/sweep and assembly/reporting. Open it from **Options → Fire type & agent guide**, or the guided Coach's second page. It remains unavailable as unrecorded assistance during recall.

The examples are authored and do not classify fire through the camera or alter the active AR scenario. The gas/explosion example directs a new worker to withdraw and escalate. Electrical involvement is an additional hazard; the guide uses Indian Class C for gases and F for cooking oils. It does not describe ordinary water or arbitrary sand as universally suitable. These learning checks do not award certificates.

Primary references: [BIS fire-extinguisher educational material](https://www.bis.gov.in/wp-content/uploads/2024/05/Fire-Extinguisher-Corrected-16-Pg-DMS_organized.pdf), [BIS IS 2190:2024](https://services.bis.gov.in/tmp/CED20221197_21082024_1.pdf), [BIS product manual and manufacturer-declared ratings](https://www.bis.gov.in/wp-content/uploads/2024/07/PM-IS-15683-_July-24.pdf), and [London Fire Brigade portable-equipment guidance](https://www.london-fire.gov.uk/media/cnyexexn/hand-held-portable-firefighting-equipment-gn08.pdf). The public IS PDF endpoint was intermittently unavailable; electrical suitability and rating provisions were checked in the indexed primary-source extract. Site-specific trainer review remains required.

## Fixed screens and Android navigation

The native Android learner and admin screens now use a bounded viewport with explicit **Earlier / More** controls when content cannot fit. There is no swipe-to-scroll page. The home and module screens fit their main actions on one ordinary portrait screen. Long forms retain the same input fields when switching parts, and content breaks preserve whole controls instead of displaying half a button. Large text uses more parts rather than smaller type. This change covers lessons, questions, records, help, sign-in/admin forms, equipment practice and AR instruction panels; operating-system pickers retain their native behavior.

Learning explanations use a full-screen popup with one fixed **Previous / Close / Next** footer, a topic/part indicator and separate choice-feedback popups. The main AR action row stays visible, and **Placement help** works before any plane is detected. Menus and long notices use explicit pages as well.

MainActivity and AdminActivity previously overrode only `onBackPressed`, which target-36 apps cannot rely on for Android 16 navigation. The supported platform callback is now registered for internal navigation, with legacy handling retained for Android 10–12 and normal system Back at the home root. See [Android's migration guidance](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture).

Main screens maintain a saved page history. Back closes a modal first; otherwise it returns to the preceding page, and lesson pages step backward. Admin forms/details return to their list, signup returns to sign-in, and the text procedure's camera view returns to the procedure without advancing its evidence. Leaving an unfinished room mission offers **Save & return**, preserving accepted actions. **My record → Resume room practice** restores the attempt; camera anchors must be placed again. Back does not rewind scored answers or create assessment evidence.

## Basis and limits

The design adapts explanation prompts, retrieval practice and distributed practice from the [IES learning practice guide](https://ies.ed.gov/ncee/wwc/practiceguide/1). That evidence concerns educational learning; it does not establish an effect size, retention percentage or workplace transfer for this app. The changed-condition exercise is a design hypothesis to test with learners.

General safety content references include OSHA's [extinguisher-use guidance](https://www.osha.gov/etools/evacuation-plans-procedures/emergency-standards/portable-extinguishers/use), [fight-or-flee guidance](https://www.osha.gov/etools/evacuation-plans-procedures/eap/fight-or-flee) and [confined-space provisions](https://osha.prod.pace.dol.gov/laws-regs/regulations/standardnumber/1910/1910.146). These are content references, not Indian legal approval. The gas mission remains an outside-only scenario with unconfirmed rescue readiness. Virtual meters do not measure real gases; markers do not identify actual exits or establish safe distances.

New coaching content is English/Hindi. Santali translation, fluent-speaker validation, safety-trainer review, and measured learning outcomes remain outstanding. This update does not implement a third complete machinery AR mission or close the entire problem statement.

## Verification

- Android debug APK and test APK build successfully.
- 157 JVM tests pass with zero failures, errors or skipped tests, including content coverage, evidence-derived review, worker separation, practice scheduling and placement cursor invalidation.
- Twenty-seven targeted emulator UI tests pass across nine suites. They cover system Back through pages, camera views and dialogs; save/cancel and signup; guided choice feedback; automatic Hindi narration requests and persistent mute; neutral recall speech; placement help before a ready plane; fire/gas action journeys; recall restoration; worker review; synthetic placement rendering; and sensor permission presence.
- Pagination tests check that all fourteen actions remain fully visible and reachable, form input survives part changes and a swipe does not change the page. Main navigation checks the home fits on one part and exercises explicit page controls instead of scroll gestures.
- Settled emulator home, module and Hindi coaching screenshots were inspected. At 200% text size in landscape, the coaching popup retains readable text and a fixed footer. Issues found during implementation, including partial buttons and duplicate paging controls, were corrected before the passing runs.
- The emulator reported actual offline English speech playback. On the Samsung, logs confirm selection and playback of installed offline English and Hindi voices; the user confirmed audible automatic Hindi instructions.

No instrumentation, camera test data or assessment records were injected into the user's physical phone. The fixed-screen candidate was installed over the existing app without clearing data. Physical checks confirm the home fits, system Back returns within the app, the Hindi placement-help popup opens, tracking recovers after the popup, and the new build places two stations. Logs show overlapping footprint candidates rejected by the new spacing rule before the second placement; the user confirmed automatic Hindi instructions. Earlier three-anchor stability and completed fire/gas branches are described above; the final candidate has not yet repeated a complete three-station mission or the extinguisher discharge branch. Learning effectiveness needs a trainer-observed comparison of unaided action sequences immediately and after a delay, including whether learners transfer the stopping decision to the changed scenario.
