# Suraksha Saathi — solution blueprint

## 1. The product decision

Build a **voice-led, offline training and evidence platform**. A worker rehearses a hazardous situation in a designated safe area, makes consequential decisions, retries specific mistakes, and earns a clearly scoped training record. A supervisor sees the decisions behind the result and records any required practical observation.

The central product loop is:

**Hear → recognise → act → understand the consequence → retry → demonstrate → verify → refresh.**

AR is useful because it makes spatial relationships and action sequences tangible: choosing between exits, keeping clear of a simulated hazard, locating safety equipment, and recognising when entry must be refused. The lasting value is evidence of understanding that works with shared phones and poor connectivity.

Suggested name: **Suraksha Saathi / सुरक्षा साथी**. This is a working name; trademark and domain availability have not been checked. Use a Santali name only after community review.

Suggested pitch: **“A pocket safety training centre that lets workers practise dangerous decisions safely, in their own language, and gives employers verifiable evidence of what they understood.”**

## 2. Correct the brief before presenting it

Several statements in the supplied background should remain labelled “problem-statement claims” until their original sources are obtained:

- Retention below 20% after one week.
- 48 fatal mine accidents in Jharkhand in 2022–23.
- A large share involving workers with fewer than 30 days of orientation.
- No existing standardised digital regional-language platform.

Do not use those figures as independently established facts or claim a first-of-its-kind product. DGMS's indexed 2024 Standard Note reports a narrower series for Jharkhand coal mines: 7 fatal accidents in calendar 2022 and 10 in 2023. That is not the same population or necessarily the same reporting period as the supplied claim. Fatal accidents and persons killed are different measures. Reconcile the original source before putting a number on a pitch slide. [DGMS Standard Note, 2024](https://www.dgms.gov.in/writereaddata/UploadFile/Standard%20Note%202024.pdf).

Update the legal framing. India Code records enforcement of the Occupational Safety, Health and Working Conditions Code, 2020 from 21 November 2025. Official government material also lists the Central Rules, 2026. Map each deployment to the applicable current rules, saved provisions, state requirements, role requirements, and site training scheme. Do not hard-code a universal certification period or describe the older Acts as the entire current framework. [India Code](https://www.indiacode.nic.in/indiacode/handle/123456789/22041?view_type=browse), [official Central Rules listing](https://labour.maharashtra.gov.in/en/labour-code/occupational-safety-health-and-working-conditions-code-central-rules-2026).

## 3. People and real operating conditions

| Person | Need | Product response |
| --- | --- | --- |
| First-time recruit | Understand actions without reading a long manual | Short narrated scenes, pictorial choices, guided rehearsal |
| Contract worker | Continue training across employers and intermittent connectivity | Portable worker reference, locally saved progress, scoped credentials |
| Worker using a shared phone | Access without owning a phone or email address | Worker card plus short PIN; supervised identity recovery |
| Trainer | Run a cohort without entering every answer for workers | Offline roster, content distribution, separate assessor mode |
| Safety officer | Find missing training and misunderstandings | Role-based requirement matrix and individual decision evidence |
| Auditor | Check provenance and current credential state | Signed record, content/rubric version, issuer and revocation status |
| Content reviewer | Correct unsafe or misunderstood material | Versioned review, translation approval, pack withdrawal |

Worker registration should require only a locally meaningful identity and employer/training-centre assignment. Do not require Aadhaar, biometrics, an email account, or a personal smartphone. Separate a person's stable worker record from site-specific employment assignments.

Allow anonymous practice. Require enrolled identity for a named assessment. For higher-assurance issuance, an authorised assessor checks identity using the site's established process. A card and PIN alone cannot eliminate impersonation.

## 4. Worker experience

The home screen answers one question: **“What should I practise next?”**

1. Select Hindi or Santali, with a spoken sample. English is available for trainers and demonstration.
2. Scan a worker card or select the preloaded roster identity and enter the PIN.
3. See one assigned lesson, its approximate duration, download readiness, and the next action.
4. Confirm the session is in a designated safe training area.
5. Place the practice scene on a training mat or recognised surface.
6. Hear a brief explanation, perform guided actions, then enter an uncoached assessment.
7. See strengths and the particular action to practise again.
8. Receive a pending result offline; obtain an issuer-signed credential when the result is validated.
9. Return for short refreshers scheduled initially at 1, 7, and 30 days. This is a proposed learning schedule, not a statutory interval.

Keep the primary navigation to **Learn / My record / Help**. Trainer mode is separately authenticated. No worker leaderboard, public failure ranking, or time-pressure reward.

### Interface direction

- Ink & Cobalt defaults to a light interface: porcelain surfaces, navy typography and cobalt actions, independent of the surrounding app's theme. Green communicates safe results, amber communicates caution or pending validation, and red communicates unsafe actions or blocked routes. See the [design system](design-system.md) and [colour tokens](../design/tokens.json).
- Large illustrations showing equipment and actions. Avoid decorative game graphics that obscure the surroundings.
- Aim for 56 dp primary action targets, adjustable text, strong contrast, and a persistent replay-audio control.
- One instruction and one decision at a time. Display the same choice with picture, text, and optional narration.
- Show “Training simulation” throughout AR. Distinguish downloaded, saved locally, pending upload, and verified.
- Keep an obvious pause/exit control. Pause scoring on tracking loss, interruptions, thermal degradation, or permission changes.
- Provide seated/tabletop operation. A worker should not have to walk backwards while looking through a phone.

## 5. Five-domain curriculum

Domains 1 and 2 are explicit in the supplied brief. Domain 3 expands the truncated “Machinery”; domains 4 and 5 are proposed and must be reconciled with the complete official statement.

| Domain | What the worker must demonstrate | Sector adaptation | First release |
| --- | --- | --- | --- |
| 1. Fire & Explosion Response | Raise alarm, choose a safe response, identify an exit, select appropriate equipment when authorised, evacuate and account for people | Coal conveyor, steel maintenance bay, mica store | Complete AR module |
| 2. Gas Leak & Confined Space Protocol | Recognise exclusion zones, interpret simulated instrument status, choose PPE appropriately, understand permit/attendant/rescue requirements, refuse unsafe entry | Mine service gallery, steel vessel, processing pit | Complete AR module |
| 3. Machinery & Energy Isolation | Identify pinch points, distinguish stopping from isolation, recognise stored energy, verify authorised isolation before intervention | Conveyor, crusher, rolling machinery | Next full module |
| 4. Falls, Haulage & Suspended Loads | Recognise unstable access, vehicle blind spots and exclusion zones; select safe routes | Mine haul road, crane bay, maintenance platform | Later release |
| 5. Dust, Heat & Occupational Exposure | Recognise exposure controls, select suitable PPE, report symptoms, follow heat/exposure procedures | Coal dust, mica processing dust, steel heat | Later release |

Use a common competency model with **separate reviewed sector scenarios**. Do not assume a steel-vessel procedure is appropriate for underground coal operations. General awareness modules must not claim to qualify specialist rescue personnel, electricians, equipment operators, or blasting personnel.

## 6. Complete module A: Fire & Explosion Response

**Scenario:** A small simulated fire begins near a training conveyor. The learner must assess whether their role and the conditions permit a limited response, or whether immediate withdrawal is required. The environment may change so the initially obvious exit becomes unavailable.

**Duration target:** 8–12 minutes including instruction and assessment. The duration is a design estimate.

**Physical setup:** A safe indoor training area, printed origin mat, exit A and exit B markers, and an assembly-point marker. A trainer places and confirms these. A virtual extinguisher is a learning prop; actual technique is assessed separately with approved training equipment.

| Stage | AR interaction | Evidence recorded | Unsafe action / recovery |
| --- | --- | --- | --- |
| Orientation | Look around the placed scene; identify alarm, two exits, and assembly point | Objects correctly identified; prompts used | Missing tracking pauses the scene |
| Recognise | Select the simulated hazard and raise the alarm | Alarm action and its order | Ignoring the alarm requirement fails the relevant critical gate |
| Decide | Choose withdrawal or an authorised incipient-fire response based on scenario conditions | Decision, role, hazard state, rationale choice | Advancing into smoke or choosing to fight an unsuitable fire terminates the attempt safely |
| Select | For the authorised branch, inspect equipment labels and select the reviewed extinguisher option | Equipment and hazard compatibility | Unsafe selection triggers a critical failure |
| Practise | Use virtual pull/aim/squeeze/sweep actions with an escape route available | Order, aiming region and action completion | Incorrect sequence gives coaching in practice; assessment records it without a hint |
| Reassess | Respond to a simulated escalation or blocked exit | Whether the worker abandons the response when needed | Continuing toward a blocked route or escalating fire is a critical failure |
| Evacuate | Choose the unblocked route and sequence withdrawal to the assembly point | Route choice and action sequence | Chasing belongings or re-entry produces remediation |
| Account | Report arrival and a missing colleague to the responsible person | Accountability action | Re-entry to search is rejected; escalate to responders |
| Transfer | Repeat a new layout without coaching | Competency-specific result on an unseen configuration | Fresh attempt required after remediation |

The withdrawal branch must have enough assessment coverage to earn credit without requiring the worker to fight a fire. Do not punish a correct decision to evacuate. Branches have explicit required competencies and normalised scores so skipped, inapplicable actions do not reduce the score.

### Scenario variants

- Small local fire with an authorised role, suitable equipment, and a clear escape route.
- Smoke or rapidly escalating conditions: raise the alarm and withdraw.
- Simulated explosion risk: evacuation and reporting, with no extinguisher mini-game.
- Blocked primary exit: reassess and select the approved alternate.

The app does not certify a real exit as safe. It uses trainer-defined training geometry, with a visible simulation label. US OSHA's emergency preparedness material supports the distinction between roles, alarms, evacuation, and designated response; it is a supplementary design reference, not Indian legal authority. [OSHA emergency preparedness](https://www.osha.gov/emergency-preparedness/getting-started).

### Assessable critical gates

Alarm/notification, appropriate response decision, avoidance of unsafe equipment use, avoidance of blocked/hazardous routes, withdrawal when required, and assembly/accountability. The exact gates must be signed off by the site's qualified safety reviewer.

## 7. Complete module B: Gas Leak & Confined Space Protocol

**Scenario:** The learner is assigned a simulated inspection near a vessel or enclosed service area. An instrument panel displays a hazardous or unverified atmosphere. A colleague suggests going in quickly. The correct decision depends on the entry prerequisites, not the colleague's confidence.

**Duration target:** 10–12 minutes including instruction and assessment.

**Physical setup:** Tabletop vessel or floor marker, an entry boundary, virtual permit board, simulated gas instrument, PPE choices, and attendant station. No real confined-space entry or deliberate gas release is involved.

| Stage | AR interaction | Evidence recorded | Unsafe action / recovery |
| --- | --- | --- | --- |
| Recognise | Identify the space boundary and potential hazards | Correct recognition and exclusion decision | Crossing the simulated boundary before clearance is a critical failure |
| Check instrument | Inspect simulated instrument readiness and measurements | Recognises invalid/unverified status and requests authorised testing | Treating absent or unreliable readings as safe is a critical failure |
| Evaluate entry | Review permit, isolation, ventilation, authorised atmospheric assessment, and rescue arrangements | Missing prerequisites identified | No valid prerequisites means no entry |
| Choose PPE | Select role- and hazard-appropriate PPE from reviewed choices | PPE match and explanation | A dust mask must not be accepted as protection from oxygen deficiency or an unknown toxic atmosphere |
| Assign roles | Place the attendant outside and establish communication | Entrant/attendant distinction and communication check | Sending both people inside fails the role gate |
| Decide | Accept a fully authorised scenario or refuse an unsafe one | Decision and all gate conditions | A good overall quiz score cannot override an unsafe entry decision |
| Respond | A simulated alarm or incapacitated colleague appears | Withdrawal, notification, and emergency escalation | Unplanned rescue entry is a critical failure |
| Debrief | Review the causal chain of one missed prerequisite | Completed remediation | Repeat with a different missing prerequisite |

Represent gas readings as **simulated instrument data**. Use instrument status, hazard categories, and site-reviewed numeric scenarios. Do not implement universal gas thresholds copied from an unrelated standard. A phone camera cannot measure oxygen, methane, or carbon monoxide.

### Scenario variants

- Missing permit despite an apparently normal reading.
- Instrument not ready or measurement status unknown.
- Complete permit but no attendant or no viable rescue arrangements.
- Alarm after entry is authorised: withdraw and notify according to the reviewed procedure.
- Incapacitated colleague: raise the alarm and summon trained rescue; no impulsive entry.

The buddy system is only one element of confined-space protection. It cannot substitute for isolation, atmospheric assessment, entry control, an attendant, and rescue arrangements. These distinctions are reflected in OSHA's confined-space standard; use Indian/site requirements for operational content. [OSHA confined-space standard](https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.146).

## 8. Assessment that tests decisions

Use three layers:

1. **Guided practice:** unlimited, narrated, repeatable. Hints and errors are expected.
2. **Uncoached simulation assessment:** unseen reviewed variants, no directional hints, recorded actions and decisions.
3. **Practical observation:** an authorised person checks the skills that cannot be established by screen interaction.

For each module, define a versioned competency rubric. An initial proposed score is 40% procedural sequence, 30% hazard/response decisions, 20% equipment and role choices, and 10% transfer questions. Normalise only over applicable criteria. Pilot and revise this rubric with safety reviewers.

**Proposed simulation pass rule:** at least 80/100, all required criteria attempted, and every applicable critical gate passed. This is a product threshold, not a statutory one. No speed bonus. A critical failure stops the hazardous branch, explains the consequence in debrief, and assigns targeted practice. The original failure stays in the record; a retry is a new attempt.

Timing can identify an interruption or a sequence anomaly, but should not independently penalise a slower reader or a worker using audio. Accessibility accommodations are recorded and must not silently lower critical safety requirements.

Produce evidence such as “Selected the alternative exit after the primary route became blocked” or “Attempted entry while instrument status was unknown.” Avoid opaque “AI readiness” scores.

Randomised, reviewed variants reduce simple answer memorisation. They do not make an offline client tamper-proof. Server replay validates event consistency; witnessed assessment increases identity assurance. Neither proves real physical competence by itself.

## 9. A certificate with honest scope

Create a **training passport** containing independent module records. Completing fire training does not confer completion of confined-space training. A credential may state:

> Fire & Explosion Response — simulation assessment passed. Practical observation: pending. Issued by [training organisation]. Content version [x].

When practical observation is completed, issue a new record or signed endorsement with the assessor and checklist reference. Site authorisation remains a separate employer decision.

### Credential lifecycle

**Local result → pending validation → simulation credential issued → practical endorsement, where applicable → expiry / revocation / supersession.**

- The phone can generate a printable **pending completion receipt** offline. It must visibly say that issuer validation is pending.
- The server validates the result, records the exact content and rubric versions, and signs the credential.
- A previously issued signed credential remains available on the phone offline.
- Offline verifiers can check the signature using cached issuer public keys. They must show when revocation information was last updated and must not claim live validity.
- If fully offline authorised issuance is later required, add a dedicated trainer device with constrained delegated signing authority. Treat this as a separate security feature, outside the first release.

The certificate includes a random credential ID, pseudonymous worker reference, module/competencies, assessment mode, practical status, issuer, issue time, configured validity interval, content and rubric versions, and a QR code. Do not put phone numbers, addresses, raw attempt logs, or Aadhaar details in public QR data.

A standard phone scanner opens a minimal web verification page. The app scanner additionally verifies the embedded signed payload offline. The page distinguishes **authentic signature**, **current status**, **identity match**, and **scope of qualification**. A copied genuine QR proves the same credential, not ownership by its presenter.

Public verification reveals minimal information. Detailed worker identity and evidence require authorised access. Employer acceptance, statutory recognition, identity checks, and practical authorisation must never be implied merely by a green tick.

## 10. AR without excluding workers

Android version is not enough to establish ARCore support. Google maintains a device compatibility list and defines an “AR Optional” distribution mode that permits a non-AR experience. Runtime and first-install checks are still needed. [Supported devices](https://developers.google.com/ar/devices), [AR Optional guidance](https://developers.google.com/ar/develop/java/enable-arcore).

| Mode | Intended capability | Design limit |
| --- | --- | --- |
| ARCore spatial mode | Surface/marker anchored scenes on tested compatible phones | Needs installed AR services and successful tracking |
| Marker AR mode, later increment | Camera overlays registered to a printed fiducial on additional tested devices | Marker must remain visible; requires a separate implementation and camera validation |
| Interactive 3D mode | Full decision scenarios on tested Android 10+ devices without working AR | Record as non-AR; do not claim a spatial skill was assessed |

Ship ARCore plus 3D fallback first. Add independent OpenCV marker tracking when device surveys justify it. ARCore's own image tracking does not bypass ARCore compatibility. OpenCV offers fiducial detection and pose estimation, but this still needs camera calibration/validation and device testing. [OpenCV ArUco documentation](https://docs.opencv.org/4.x/d5/dae/tutorial_aruco_detection.html).

Prepare devices online before field sessions: install the app and compatible AR runtime/profile data, provision the roster and trust keys, download content, then test in airplane mode. An unsupported or unprepared phone must offer the 3D path. Do not advertise universal first-launch offline AR.

Use a printed training mat to establish scale and a known origin. For room-scale practice, trainers place and validate separate markers. Pause when tracking fails; do not leave a floating arrow that appears to be a reliable exit instruction.

Ordinary phones are not assumed to be intrinsically safe. Train in approved safe areas, using inert props and simulated hazards. DGMS maintains equipment approval guidance for mine use; any operational use of electronic equipment in hazardous areas needs the applicable approval and site controls. [DGMS approval resources](https://www.dgms.gov.in/UserView/index?mid=1647).

## 11. Hindi and Santali as complete experiences

Localise the entire journey: onboarding, safety introduction, learning audio, choices, feedback, errors, result labels, certificate explanations, and help. A translated menu with an English lesson does not meet the requirement.

- Use reviewed Hindi text and recorded speech.
- Use Santali speech recorded by local speakers, with Ol Chiki text as the initial proposed script and alternate script needs established through field interviews.
- Store separate stable content IDs and language assets; keep assessment semantics identical across locales.
- Bundle licensed fonts, verify glyph coverage and rendering, and test line breaks on target phones.
- Create a terminology glossary jointly reviewed by a safety expert and Santali speakers. Back-translate high-risk instructions and test comprehension with intended users.
- Offer slower replay, captions, pictorial responses, and a choice to switch language without losing progress.
- Use audio selection without mandatory speech recognition. Background noise and language-model errors must not decide whether a worker is competent.
- Display missing translations as a release blocker. Do not silently substitute Hindi under a Santali label.

AI translation may assist editorial drafts, but safety content must pass human review. A cloud speech service is not a core runtime dependency. No certified Santali copy or audio has been produced in this design package.

## 12. Admin dashboard: evidence and follow-up

Design the dashboard around action, with summary metrics leading to named records:

| View | Answers | Main action |
| --- | --- | --- |
| Requirement matrix | Which required modules are missing, expired, pending or complete for each role/site? | Assign training or practical observation |
| New-recruit cohort | Who is still completing induction in the first 30 days? | Schedule the next session |
| Critical mistakes | Which decision gates are repeatedly missed, with what denominator? | Assign targeted refresher content |
| Worker record | What was attempted, understood, retried and observed? | Inspect evidence and sign an authorised checklist |
| Credential register | Which records are issued, withdrawn, expired or awaiting validation? | Verify, revoke with reason, or review |
| Content management | Which languages, SOP mappings and review versions are active? | Approve and publish a signed pack |
| Sync health | Which devices have stale or pending data? | Resolve sync and storage issues |

Every metric shows its time window and scope. “Not synced” is different from “not trained.” Report training completion separately from practical sign-off and site authorisation. Label compliance views “training requirements status” until the applicable compliance mapping is approved.

Allow CSV exports and a worker evidence report with module versions and assessor history. Use tenant/site/role permissions, append-only audit events, and configurable retention. Do not use ethnicity or language as a worker-risk score. Aggregate language-level usability findings only with adequate group size and appropriate access.

## 13. What makes this credible

1. **Decision evidence:** a critical unsafe choice cannot be hidden behind a high quiz score.
2. **Offline continuity:** learning, assessment, results and existing credentials work after preparation without a network.
3. **Meaningful language support:** locally reviewed speech and pictorial interaction are central to the lesson.
4. **Portable, scoped records:** a worker carries proof of specific training, with practical status visible.
5. **Site adaptation:** reviewed sector and site packs change scenarios without replacing the whole application.
6. **Measured refreshers:** revisit missed competencies and evaluate delayed retention instead of claiming it.

A later AI assistant may retrieve approved explanations for trainers and draft content for review. It must not generate live emergency instructions, set pass/fail decisions, infer real gas hazards, or grant authorisation.

## 14. Deployment and sustainability

Begin with one participating training centre and a limited worker cohort. Include two sector variants, then expand after observing device compatibility, language comprehension and practical transfer. Local mining/industrial training organisations, ITIs, employers and Santali educators are potential partners, not established partnerships.

Provide free worker access under an institutional deployment model. Charge institutions for onboarding, reviewed site packs, hosting, support and maintained compliance mappings. Pricing should follow interviews and measured support costs; no unvalidated subscription figure is part of this proposal.

For small contractors, offer pooled training-centre devices and reusable printed materials. Content and signed update packages can be imported through approved file transfer without placing a cloud connection in the training path. Keep certificates portable when a worker changes employer, subject to identity and issuer trust checks.

The first commercial proof is that a training centre can run a session, identify misunderstood procedures, and retrieve trustworthy evidence with less administrative work. Accident reduction requires a larger and longer evaluation than a hackathon or short pilot can establish.
