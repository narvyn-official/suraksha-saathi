# Suraksha Saathi — delivery and demo plan

This is a proposed plan. None of the acceptance checks below have been executed against an Android implementation.

## 1. Scope that can win a demonstration

Complete the vertical path from a worker's first lesson to a verifiable record and an actionable dashboard. Prioritise two deeply implemented modules over five shallow scenes.

| Required output | First-release definition of done |
| --- | --- |
| Android APK | Installable signed release APK, minimum Android 10, tested on named physical devices |
| Two complete AR modules | Fire and confined-space flows each include placement, narration, guided practice, branching assessment, critical failure, remediation, result and save/resume behaviour |
| Assessment engine | Versioned rubrics, critical gates, fresh attempts after failures, server replay consistent with local results |
| QR credentials | Issued signed record, online verification, offline authenticity check, tampered QR rejection and revocation demonstration |
| Hindi and Santali | Complete reviewed text and recorded audio for all mandatory flows; Ol Chiki rendering verified where used |
| Offline function | Provisioned airplane-mode training, assessment, saved progress, pending receipt, access to issued credentials; sync after reconnect |
| Web admin | Scoped roster, requirements status, attempts, critical mistakes, practical observation, certificate register and export |
| Demo video | Actual phone camera AR, offline proof, critical-failure behaviour, sync and certificate verification |
| Public GitHub | Reproducible source, releases, setup instructions, demo fixtures, source and asset attribution |

If the evaluator requires issuer-signed *new* certificates while completely offline, obtain that interpretation early and budget delegated trainer-device issuance. The initial design issues pending receipts offline and official signed credentials after validation.

## 2. Proposed six-week build

Assumption: a team of approximately 4–6 contributors covering Unity/Android, backend, web, 3D/UX, and testing, plus scheduled access to safety and Santali reviewers. This is a planning estimate, not a guaranteed delivery date.

| Week | Working increment | Exit criterion |
| --- | --- | --- |
| 1 | Device spike, training-area layout, scenario/rubric drafts, native storage bridge | A scene anchors on the weakest reference phone; a saved event survives app kill; reviewers accept module learning objectives |
| 2 | Complete fire module in one language | Full guided and uncoached path, including safe withdrawal and critical failure |
| 3 | Complete confined-space module and shared engine | Both modules use the same engine; valid and unsafe branches pass deterministic checks |
| 4 | Sync, signed credentials, admin and 3D fallback | Airplane-mode result reaches the dashboard after reconnect; QR verifies and tampered QR fails |
| 5 | Full Hindi/Santali, shared-device flow, practical checklist | Local reviewers sign off; intended users complete the flow with recorded usability observations |
| 6 | Device hardening, pilot rehearsal and submission | Required acceptance checks pass; APK, video and repository instructions agree |

Content review and localisation preparation begin in week 1 and continue throughout. Waiting until week 5 to find Santali speakers would put the submission at risk. If reviewers are unavailable, mark the corresponding content incomplete rather than claiming localisation.

Next increment: machinery isolation module, independent marker AR, more sector packs, and managed offline trainer issuance if required. A free-form AI tutor, predictive accident scoring, blockchain, live hazard detection and full-site digital twins are outside the first release.

## 3. Acceptance and failure tests

| Check | Expected result |
| --- | --- |
| Unsupported ARCore device | App still starts and offers accurately labelled 3D mode |
| AR runtime absent / camera permission denied | Clear recovery or fallback; no broken camera screen |
| Tracking lost midway | Assessment pauses; reacquisition does not create phantom actions |
| Critical unsafe decision with otherwise high score | Assessment fails the critical gate |
| Correct evacuation branch | Can pass without attempting firefighting |
| User taps through without required actions | No completed assessment or credential |
| Same attempt uploaded repeatedly | One acknowledged attempt and at most one corresponding credential |
| Upload succeeds but response is lost | Retry receives the original durable result |
| Altered QR payload or untrusted signing key | Verification fails or reports an unrecognised issuer |
| Old cached revocation state | Offline verifier reports freshness limits, not live validity |
| Revoked credential scanned online | Shows revoked, with no active/valid badge |
| Completed attempt references an unapproved pack | Kept for review; no official issuance |
| Hindi/Santali missing critical prompt | Pack cannot be approved or released |
| App killed after a saved step | Last committed progress is recoverable; no false completion |
| Another tenant's worker ID used in API/export | Access denied; no data leaked |
| Practical observation missing | Certificate and dashboard continue to show it as pending |
| Two devices train the same worker | Both attempts preserved without replacing each other |
| Interrupted content import | Previous working pack remains active |
| 10-minute AR session | Performance and thermal behaviour documented on each reference device |

Test with at least one weaker compatible phone, a second vendor's compatible phone, and a device without ARCore support. Include lighting variation, poor connectivity, low storage, text scaling, shared-phone switching and a complete offline cycle. An emulator is useful for logic and UI checks but cannot substitute for camera AR validation.

## 4. Pilot: measure learning honestly

Start with a feasibility cohort, for example 30–50 consenting workers across relevant roles and language preferences. This is a planning range, not a powered impact study.

Measure baseline decisions, immediate post-training decisions, seven-day and thirty-day retention, trainer-observed transfer, help needed to finish, failed tracking, and critical mistakes by opportunity count. Use alternate but equivalent scenarios to reduce memorisation. Record dropout and missing follow-up data.

Where feasible, compare with the existing training approach using comparable or randomised groups while preserving all required safety training. Predefine the primary outcome and analysis before collecting results. Report confidence intervals and sample limitations. Do not equate app completion with accident prevention.

Proposed usability success criterion: most participants can complete a lesson without the trainer operating the phone for them. Set the numeric threshold after an initial usability round. Report actual results, not an assumed 90% retention improvement.

Learning results should support coaching. A failed simulation should not automatically trigger punitive employment decisions.

## 5. Five-minute demonstration script

| Time | What judges see | What it proves |
| --- | --- | --- |
| 0:00–0:25 | A short worker story, shared Android phone and printed training mat | Concrete user and affordable setup |
| 0:25–0:45 | Hindi/Santali selection and prepared content, then airplane mode | Local language assets and offline readiness |
| 0:45–1:40 | Actual camera-based fire scene; alarm, response decision, blocked-exit change | Spatial interaction and meaningful sequence |
| 1:40–2:30 | Confined-space scenario; missing prerequisite and unsafe-entry choice | Critical gates override an otherwise good score |
| 2:30–3:05 | Targeted practice and a fresh, successful attempt; pending receipt | Remediation and honest offline result state |
| 3:05–3:40 | Reconnect, sync acknowledgement, dashboard evidence, signed credential | Complete data path and issuer validation |
| 3:40–4:20 | Scan the issued QR on another phone; tamper and revocation examples | Authenticity and current-status distinction |
| 4:20–4:45 | AR-disabled phone showing the 3D fallback; practical status remains visible | Compatibility and truthful scope |
| 4:45–5:00 | APK release, repository build instructions and measured device results | Reproducibility |

Compress lesson footage transparently with visible edit labels; do not suggest a full lesson takes the duration of the video. Record actual device footage rather than presenting a browser mockup as AR. Seeded workers and example dashboard data must be labelled as demonstration data.

## 6. Submission checklist

- Signed APK release, SHA-256 checksum, version and exact tested devices.
- Source code, dependency lock/pin files and build prerequisites.
- Local backend/web setup with demo records and clear credentials for the isolated demonstration environment.
- No real worker data, passwords, private signing keys or unlicensed proprietary assets.
- Two module walkthroughs, reviewed rubrics and source-to-competency mapping.
- Hindi and Santali coverage report, reviewer status and audio/font licences.
- QR signing/verification documentation and offline limitations.
- Test results, performance measurements and known issues.
- Demo video link and steps to reproduce each claim.
- README that distinguishes implemented features from roadmap items.

## 7. Present status

Completed in this design task: solution blueprint, engineering design, delivery plan and an interactive concept preview. Remaining implementation work: Android app, reviewed lesson assets and translations, backend, dashboard, actual cryptographic credential flow, device tests, demo recording and public repository publication.
