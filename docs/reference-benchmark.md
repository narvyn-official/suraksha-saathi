# Reference comparison and upgrade — 14 September 2026

Reviewed reference: [nitikash4-svg/Ar--based-model](https://github.com/nitikash4-svg/Ar--based-model/tree/0fb1db22da6d1816e8373aefde739ad5c61a4a42). This is a source review of that exact revision, not a runtime, usability or field evaluation. No reference code or assets were copied. The comparison is against Suraksha Saathi v0.2 pilot, not its unimplemented architecture plans.

## Capability evidence

| Area | Reference at reviewed revision | Suraksha Saathi v0.2 |
|---|---|---|
| Delivered worker platform | Browser React application | Native Android 10+ APK; trainer dashboard in the browser |
| Complete training domains | Fire and gas routes; other module entries marked coming soon | Fire, gas/confined spaces and machinery/isolation: three complete learning/practice/assessment flows, 24 decisions |
| Camera experience | Active fire/gas routes combine camera video with screen-positioned interactive overlays | ARCore plane placement, tracked anchors, original 3D equipment and anchored answer cards; physical-device tracking validation pending |
| 3D equipment | Machine model component exists; it is not connected to the active routes inspected | Original fire, gas and machinery meshes, connected offline orbit/zoom viewer and AR renderer |
| Offline learning | Browser localStorage saves progress; no service-worker app-shell cache was found | Curriculum, illustrations, 3D meshes and SQLite progress bundled in the APK; no network required for screen training |
| Interrupted attempts | Active assessment state held in React until completion | Every answer saved; assessment resumes across activity recreation |
| Assessment | Point threshold with question feedback | Guided practice separated from assessment; critical unsafe choices stop assessment; server recalculates imported results |
| Credentials | Verification includes a hardcoded valid demo ID; unknown certificate detail can fall back to a fabricated passed example | Signed ES256 pilot credentials; Android pins issuer key; tamper rejection, server revocation and explicit unknown offline revocation status |
| Worker management | Worker directory and charts backed by bundled sample data | Search, sector/status filters, individual histories and CSV derived from imported persistent records |
| Analytics | Dashboard uses imported static statistics | Latest assessment per worker/module, coverage chart, critical missed decisions and practice follow-up; limited-record coverage disclosed |
| Languages | English/Hindi UI dictionary, placeholder Santali; active training pages contain English text | English/Hindi pilot lessons and decisions for all three modules; Santali explicitly pending native review |
| Access/data scope | Demo login credentials and browser storage | Owner-scoped server routes and persistent D1 records; local demo identity remains development-only; production organisation roles pending |

Reference source anchors: [active routes](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/App.jsx), [modules](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/data/modules.js), [fire training](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/pages/FireTraining.jsx), [gas training](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/pages/GasTraining.jsx), [assessment](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/components/assessment/Assessment.jsx), [verification](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/pages/Verify.jsx), [certificate fallback](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/pages/CertificateDetail.jsx), [admin dashboard](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/admin/AdminDashboard.jsx), [storage](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/utils/storage.js), [translations](https://github.com/nitikash4-svg/Ar--based-model/blob/0fb1db22da6d1816e8373aefde739ad5c61a4a42/src/data/translations.js).

## Improvements made after this review

1. Added machinery/isolation curriculum with eight decisions, Hindi text, guidance on authorised work, stored energy, verification and controlled handover.
2. Added original inspectable equipment illustrations and rotatable 3D models. The same meshes are used for anchored AR equipment.
3. Added persisted worker sectors, searchable/filterable worker histories, CSV export, real coverage charts and critical-decision follow-up.
4. Preserved v0.1 content for older evidence imports. New machinery records cannot be presented as the older curriculum.
5. Added tests for latest-attempt analytics, critical failures, version compatibility, CSV formula protection and native 3D rendering.

## Where superiority is not established

The reference has browser-based worker flows; our worker training currently requires Android. There is no comparative usability study, retention trial, crash-rate benchmark, or measured device performance comparison. Real AR interaction is still decision-oriented rather than a validated physical extinguisher or isolation procedure. Three of five proposed domains are implemented. Reviewed Santali, practical assessment, automatic secure sync, multi-organisation roles, live dashboard hosting and production distribution remain open.

Therefore “many times better in every aspect” is a target, not a verified result. Stronger evidence integrity and broader implemented native training are concrete improvements; learning effectiveness and field readiness require testing with workers and competent safety reviewers.
