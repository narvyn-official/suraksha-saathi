# AR problem-statement coverage and acceptance gates

Review date: 15 September 2026. Scope: the local 0.5.4 candidate, curriculum 0.4.0, existing test reports, and publicly accessible repository/release pages. This review ran no builds, device commands or field trials. Proposed acceptance criteria below are future gates, not passed checks.

**The repository contains a substantial Android training pilot, but the complete expected solution is not yet evidenced.** The strongest new AR work is an action-driven fire/gas room mission. The largest gaps are successful physical-phone AR, full domain coverage within those missions, reviewed Santali, and a connected mission/assessment/trainer workflow. An installable APK, camera feed, 3D viewer, or completed screen simulation does not independently satisfy “two complete AR training modules.”

Subsequent engineering work is tracked separately in [0.5.5 AR technology and validation](ar-technology-and-validation.md). Its new pose, lifecycle and placement changes do not close the curriculum, language or physical-evidence gaps identified by this 0.5.4 baseline review.

The [0.5.6 recall challenge](ar-recall-challenge.md) subsequently adds optional cue fading and recorded assistance to the room missions. It addresses part of rehearsal support, but does not deliver an independent mission rubric, missing domain objectives, Santali or physical AR acceptance.

**0.6.0 update:** [The new release report](release-0.6.0.md) supersedes the specific missing-objective findings below: exit/equipment/evacuation/assembly/reporting and an announced explosion branch are now in the fire room mission; outside-role PPE and buddy communication are in gas. Saved v2 room practice can resume, and room journals import into a separate trainer view. The table below remains the dated 0.5.4 baseline. Physical AR, reviewed languages, machinery room training, practical assessment and hosting remain open.

## 1. Scope and evidence rules

The supplied description identifies fire/explosion response and gas/confined-space protocol, then stops at “(3) Machinery.” Machinery details and the remaining two official domains were not supplied. Continue with the existing five-domain implementation, but label machinery scope, PPE/exposure and emergency/reporting as provisional until the full brief is obtained. Do not describe these choices as the official DGMS syllabus.

The supplied accident, one-week retention and legislation claims have not been independently established by this review. They are not used as acceptance evidence or repeated as facts. Statutory applicability, training intervals and issuer authority need a current India/Jharkhand/site-specific review; this document provides no legal certification finding.

Use three distinct statuses:

- **Implemented:** source contains the feature. This does not imply field verification.
- **Evidenced:** an identified existing report or artifact supports the stated, limited behavior. State whether it is JVM, emulator, browser or physical-device evidence.
- **Open:** missing implementation, incomplete scope, or missing independent acceptance evidence. These must not be blended into a completion percentage.

## 2. Deliverable-by-deliverable coverage

| Expected deliverable | Implemented and inspectable evidence | Open gate |
| --- | --- | --- |
| Android 10+ application, no headset | Native Kotlin app; `minSdk=29`; ARCore optional and camera hardware optional in [manifest](../apps/android/app/src/main/AndroidManifest.xml) and [build configuration](../apps/android/app/build.gradle.kts). Native controls and screen alternatives exist. | Named Android 10 and representative mid-range physical-device runs. Android version alone does not establish ARCore support. |
| At least two complete AR modules | [RoomMission](../apps/android/app/src/main/java/com/narvyn/suraksha/RoomMission.kt), [activity](../apps/android/app/src/main/java/com/narvyn/suraksha/RoomMissionActivity.kt), [view](../apps/android/app/src/main/java/com/narvyn/suraksha/RoomMissionView.kt), three station anchors, gestures and consequences. Separate [procedure catalogue](../apps/android/app/src/main/java/com/narvyn/suraksha/ProcedureLearning.kt) has 12 fire and 9 gas steps. | Physical placement and full camera completion remain unverified. The short room missions omit some named domain objectives; separate screens do not automatically make a complete coherent AR module. See sections 3–4. |
| Five safety domains | [Bundled curriculum](../content/curriculum.json) contains fire, gas, machinery, PPE and emergency modules, each with eight assessment decisions: 40 total. Lesson, practice and assessment paths exist. | Confirm domains 3–5; approve safety content; add mining, steel and mica task variants. A stored sector label is not sector-specific instruction. Only fire/gas have action-driven room missions. |
| Assessment engine | Versioned decisions, critical-stop grading, saved answer events and server regrading of imported assessment records. [Grader tests](../apps/android/app/src/test/java/com/narvyn/suraksha/GraderTest.kt), [web grading](../apps/admin-web/lib/grading.ts). | An independent mission rubric, linked evidence across learning paths, controlled assistance reporting and competent review. Current mission completion is guided practice, not an assessment pass. |
| QR certificate generation and verification | Device completion-receipt PDF/QR; separately, ES256 issuer-signed pilot credentials, Android trust anchor/wallet, expiry and server revocation. [Issuance route](../apps/admin-web/app/api/credentials/route.ts), [dashboard setup](../apps/admin-web/README.md). | Physical cross-phone QR trials and production issuer governance. Receipt, authentic signature, current validity, learner identity and practical competence remain distinct. Offline issuance by an authorised issuer is not implemented on the worker phone. |
| Hindi | English/Hindi curriculum and substantial native UI strings; existing scoped Hindi/200% font tests and images. [Language status](../content/README.md). | Critical-flow string audit, remaining English status/debrief/PDF text, safety terminology and worker comprehension review. Installed offline TTS is conditional, not a complete approved audio pack. |
| Santali | Selector and curriculum explicitly mark `sat-Olck` native review required. | No released Santali lesson/UI/audio pack. Required translation, preferred script, glyph rendering, narration and comprehension must be reviewed with native speakers. A pending selector is not localisation. |
| Offline functionality | Bundled curriculum/meshes; SQLite assessment, review, component and procedure records; local room-mission audit; offline signature checking; manual trainer export. | Provisioning and airplane-mode end-to-end trials, interrupted installs, storage failure/recovery. AR service installation and voice packs may require advance provisioning. Room-mission process death currently starts a new attempt while retaining the earlier audit; it does not resume that mission. |
| Web admin compliance dashboard | Local React/D1 trainer directory, sector filters, assessment analytics/review, validated JSON imports, CSV and pilot credential controls. [Import API](../apps/admin-web/app/api/import/route.ts). | Public hosted deployment; room/procedure journal import; site requirements, overdue compliance rules, assessor roles, practical observations and governed audit. The present dashboard is a trainer workspace, not a verified statutory compliance system. |
| Demo video | Existing public 0.5.0 emulator walkthrough and earlier local videos. | A final video of the same tested build completing both modules on a real supported phone, plus offline/language/assessment/QR/admin proof. Old emulator footage cannot demonstrate current room AR. |
| Public GitHub repository | [Public source](https://github.com/narvyn-official/suraksha-saathi) and [0.5.0 release page](https://github.com/narvyn-official/suraksha-saathi/releases/tag/v0.5.0-pilot) were publicly accessible during this review. | Align submitted source commit, APK, demo and checksums. The public download is older than the reviewed 0.5.4 candidate. Reproduce a clean build and verify repository/asset licensing before submission. |

Google certifies specific devices for ARCore using camera, sensor and hardware characteristics. Keep a named tested-device list and an accurately labelled fallback; do not promise camera AR on every Android 10 phone. [ARCore supported devices](https://developers.google.com/ar/devices).

## 3. What the present AR paths actually teach

### Fire

The room mission accepts alarm activation, outward pin drag, fresh base alignment, held traversal across five adjacent base bands, intentional release and withdrawal when conditions worsen. The 300 ms alignment and 1.5 s sweep duration are virtual interaction parameters. They do not validate extinguisher technique, grip force, discharge range or a real response time. [Current mission scope](room-ar-missions.md).

This is meaningfully different from merely viewing a model or answering a question beside it: gestures change scene state, direction matters, interrupted input resets partial evidence, and worsening conditions change the required action.

However, this room path assumes responder authorisation, suitable equipment and a clear retreat path. It does not make the learner identify competing exits, choose an evacuation-only branch, follow an evacuation sequence to accountability, or apply a reviewed explosion-risk response. Some decisions exist in the separate catalogue/assessment. Their presence must be mapped explicitly before calling the room module complete.

### Gas/confined space

The room mission uses a labelled SIM meter, barrier deployment, an outside attendant and refusal of entry when rescue readiness is unconfirmed. [Station placement policy](../apps/android/app/src/main/java/com/narvyn/suraksha/RoomStationPlacement.kt) checks the green marker against the simulated barrier orientation and opening, including marker clearance. It does not survey a real exclusion boundary or detect an atmosphere.

The outside-only ending is an appropriate constraint for the present scenario. Yet a meter hold is an inspection gesture, not a gas-measurement interpretation test. The room path does not enact PPE selection, authorised prerequisite review or buddy communication/withdrawal signals. Those are present mainly as separate curriculum/procedure decisions. This remains incomplete against the named gas-domain objectives.

### Evidence separation that must survive integration

| Current path | What its evidence supports | What it must not imply |
| --- | --- | --- |
| Equipment viewer/component practice | Inspection, identification, assistance exposure and changed-view recognition | Manual equipment operation |
| AR decision stations | Selection of a reviewed decision while using a tracked display | Physical task completion |
| Ordered procedures | Saved sequence decisions; guided retry or independent critical stop | Continuous physical sweep or authorised entry |
| Spatial target holds | Virtual alignment/hold at discrete target zones | Continuous sweep competence |
| Room missions | Enacted virtual controls, alignment and continuous simulated trajectory | Independent mastery, live hazard recognition or practical certification |
| Assessment and signed pilot credential | Reviewed decision score and issuer authenticity within explicit scope | Verification of worker identity, current revocation while offline, or permission to work |

Connecting these paths should retain their source, mode, assistance and result types. Do not convert a completed mission journal directly into a passed assessment or overwrite a critical failure with successful guided practice.

## 4. Phased acceptance criteria

These gates are ordered by dependency. Safety/localisation review can proceed alongside engineering; a missing reviewer does not justify marking their work complete.

### Gate A — Demonstrate a reliable camera foundation

**Exit artifact:** a device matrix with build hash, device/OS/AR-service versions, test conditions, raw outcomes and short actual-camera clips.

1. Complete each camera mission three consecutive times on each of two named supported physical phones, including one representative mid-range device. Cover Android 10 on compatible hardware, and separately test a non-ARCore device's fallback. These are proposed pilot QA repetitions, not evidence of population-wide reliability.
2. Place all stations on a recent tracked plane. Turn away and return; station locations must remain spatially stable enough to select the same controls. Missed/invalid placement must not silently place or move anything. Gas outside-point checks must pass after barrier translation and rotation.
3. Interrupt a hold using tracking loss, backgrounding, a modal, resize, camera denial and critical thermal recovery. Require new valid input afterward. No fabricated release, phantom completion, duplicate event or learner error may result from a system interruption.
4. Record actual frame/sample gaps, UI latency, thermal state and battery change during a sustained ten-minute session. Explain every false continuity reset. The existing 150 ms continuity rule must not be relaxed merely to hide device failures; first fix rendering/callback work and then review any interaction-design change explicitly.
5. Show an actionable retry/fallback with preserved accepted evidence after a failed AR session. Verify stationary use, one-handed control reachability and large Hindi text in portrait/landscape. Test TalkBack separately; emulator semantic checks are not spoken-service validation.

### Gate B — Make fire a complete module

**Exit artifact:** an objective-to-scene-to-rubric map approved by a competent safety reviewer, with guided and independent runs on the accepted physical devices.

| Required objective | Observable learner behavior and pass criterion |
| --- | --- |
| Alarm and responder decision | Recognise the scenario cue, raise the alarm, and select withdrawal when not authorised or when conditions are unsafe. A safe evacuation-only path must complete without forcing extinguisher use. |
| Exit identification | Identify the designated clear virtual exit among alternatives in the training surroundings. Change the layout/obstruction for the independent variant; do not reuse a memorised screen position. |
| Extinguisher suitability and use | Review scenario role, retreat route and suitability; perform the correct illustrated pin/aim/discharge/sweep sequence with state changes tied to the action. Independently record guidance used and incomplete/invalid gestures. |
| Fire/explosion limits | Include a reviewer-approved escalation/explosion-risk scenario that ends in withdrawal/escalation, without inviting close approach or testing workers around real hazards. Do not treat every fire as suitable for the same extinguisher. |
| Evacuation sequencing | Stop discharge, select the safe training route, reach the assigned virtual assembly/accountability step and report; reject re-entry/blocked-route choices in the independent rubric. Virtual markers must never be presented as real emergency navigation. |
| Remediation and evidence | Explain the missed cue after a practice error; preserve the original independent failure and require a new attempt. Include a changed-layout attempt and later recall without answer-revealing cues. |

The role distinction and equipment-suitability checks have a general technical basis in OSHA's employer-designated responder and extinguisher-selection guidance; they are not a determination of Indian legal requirements. [OSHA extinguisher requirements](https://www.osha.gov/etools/evacuation-plans-procedures/emergency-standards/portable-extinguishers/required).

### Gate C — Make gas/confined space a complete module

**Exit artifact:** a reviewed full prerequisite/role scenario, an explicit outside-only failure branch, and physical-camera evidence for the full module.

| Required objective | Observable learner behavior and pass criterion |
| --- | --- |
| Hazard-zone recognition | Identify the simulated opening/boundary and deploy exclusion on the correct side. A changed barrier orientation must not turn an inside location into a credited outside position. |
| Meter and entry prerequisites | Recognise SIM as a scenario cue; request/review authorised atmospheric assessment and applicable controls rather than treating camera imagery as measurement. Missing permit/isolation/assessment prerequisites keep entry closed. |
| PPE selection | Select reviewed task/role-appropriate protection using an actual interaction with alternatives and explanatory feedback. A generic mask must not grant access to an unknown atmosphere. Labels and narration must communicate the assessed scenario, not prescribe universal equipment. |
| Buddy/attendant procedures | Assign the attendant outside, rehearse a communication/withdrawal signal and demonstrate escalation when support is missing. A paired rehearsal remains distinct from each individual's assessment. |
| Rescue-readiness failure | The unconfirmed-rescue branch ends outside with refusal and escalation. Both the rendered scene and audit must agree that no entry was authorised; completing earlier checks cannot override the missing prerequisite. |
| Independent transfer | Reposition the scene and remove answer-revealing guidance. Score reviewed decisions and virtual actions separately; invalid tracking is a system interruption, not an unsafe learner choice. |

OSHA's confined-space text distinguishes atmospheric/control requirements, outside attendants, communication and rescue-service readiness. These support the general design separation; Indian/site-specific applicability requires competent review. [OSHA 1910.146](https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.146).

### Gate D — Complete the five-domain and language contract

1. Obtain the full official domain text. Until then, keep the existing proposed domains visible and clearly provisional. For each confirmed domain, map every objective to lesson, enacted/decision practice, independent assessment, critical errors, remediation and safety source/reviewer.
2. Produce reviewed coal/mining, steel and mica scenarios for relevant tasks. Validate terminology and work roles with intended users. Do not infer competence, literacy or preferred language from tribal identity or recruitment status.
3. Audit every mandatory English/Hindi/Santali screen, gesture hint, error, safety message, assessment, offline state, QR result and debrief. No missing critical string may silently fall back to another language in an approved pack.
4. Obtain native-speaker and safety review of Santali, including preferred script, Ol Chiki display where used, line wrapping and required audio. Bundle approved assets and licences; test without network or pre-existing speech voices. Provide equivalent captions/text and replayable narration without grading accent or speech recognition.
5. Validate language versions against the same underlying concepts with workers, including users unfamiliar with smartphones. Keep accessibility/text alternatives usable and correctly attributed.

### Gate E — Connect offline evidence, assessment, QR and admin

**Exit artifact:** a reproducible worker-to-trainer trace using one synthetic learner and separately labelled evidence types.

1. Provision once, enable airplane mode, complete a module and assessment, restart the app and inspect committed progress. For room-mission process death either implement validated recovery of completed stages while resetting gestures, or explicitly mark the attempt interrupted and start a new one; do not label an audit snapshot as resumable execution.
2. Export/import room and ordered-procedure journals alongside assessment records without merging their credit. Preserve worker, attempt ID, curriculum/rubric version, camera/screen/text mode, assistance, interruptions and result. Repeat an import and lose an acknowledgement: retain one durable record, with conflicts surfaced.
3. Inject a save failure and switch learners during incomplete work. No unsaved advancement or cross-worker evidence may appear. Preserve the previous accepted state and give a usable retry.
4. Demonstrate server regrading and critical-stop enforcement; guided rehearsal cannot issue a credential. A passed independent knowledge/simulation assessment may receive only the explicitly scoped pilot credential. Practical status remains pending/not assessed unless a separately authorised assessor records an observation under a reviewed policy.
5. Show receipt generation, signed pilot QR issuance, cross-phone verification, tamper rejection, unknown issuer, expiry and revocation. Offline verification must state its revocation/time limitations. Clarify whether the submission requires new issuer-signed certificates while fully offline; that capability is currently absent from the worker app.
6. Host the dashboard with approved identity/organisation roles and persistent data. Demonstrate worker requirements, completed/failed/pending training, critical follow-up and credential state. A roster or chart alone is not a complete compliance workflow.

### Gate F — Publish a coherent submission and evaluate learning

Publish one identified source commit, matching installable APK, SHA-256 file, build instructions and actual-phone demo. Include both AR modules, Hindi and reviewed Santali, offline behavior, an unsafe assessment branch, trainer review and QR verification. Label cuts, synthetic records, screen fallback and known device limits. Do not substitute older emulator footage for current AR proof.

Before claiming better retention, evaluate delayed independent performance with equivalent unseen tasks. Roediger and Karpicke found delayed recall benefits from retrieval practice in student prose-learning experiments; this supports testing a retrieval-based design, not a numerical learning gain for this app or an industrial-skill claim. [Original study](https://pubmed.ncbi.nlm.nih.gov/16507066/).

Use the existing [learning-design protocol](ar-learning-evidence.md) for formative worker sessions followed by a pre-specified comparison of delayed critical decisions and trainer-observed transfer. Match content, language and feedback between conditions; if claiming an AR-specific benefit, separate AR display effects from the practice design. Report missing follow-ups and uncertainty. Guided mission completion, visual realism, satisfaction and immediate quiz scores cannot alone establish safe transfer or accident reduction.

## 5. Evidence available now, and its limits

- The existing JVM XML reports inspected during this review contain **93 tests in 14 suites, zero failures/errors/skips**, including 13 `RoomMissionTest` cases. These reports were read, not rerun. Their presence establishes reported test execution, not physical-camera validity.
- [0.5.4 validation notes](ar-rendering-and-placement.md) report eight Android test methods on an API 36 emulator with host graphics. This includes complete screen fire/gas interactions and scoped recovery/layout checks. The software-emulator sweep failure and the hardware-graphics result are both documented; the evidence thresholds were not weakened.
- The existing local APK's SHA-256 was recomputed in this review and matches the local `artifacts/SHA256SUMS-0.5.4.txt` file: `f84347013c21c92d1c02e739f8d5daeb860adc3c111569dbe85ae98b9efbc39b`. This verifies artifact identity only.
- The recorded Samsung SM-S921B test reached a live camera feed but not usable station placement. Delayed sensor poses and thermal throttling were observed; causation and a successful physical fix remain unconfirmed. [Physical recovery evidence](ar-recovery-validation.md).
- The public repository and 0.5.0 release page were accessible. Current local room-mission work is ahead of that public APK/video. This review did not download or verify public release asset bytes.
- The older [requirements tracker](requirements-tracker.md) predates some room-mission improvements, and the final “Present status” paragraph in [delivery plan](delivery-and-demo.md) still describes the original design stage. Treat them as historical/planning material where they conflict with current source and dated evidence; update them before submission.

**Next acceptance priority:** prove current three-station camera behavior on the target hardware while safety/localisation reviewers map the missing fire/gas objectives. Then connect reviewed mission evidence to the trainer workflow and release the matching APK/video. Adding more decorative models or claiming a performance multiplier over the reference repository will not close these gaps.
