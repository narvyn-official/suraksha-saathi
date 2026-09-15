# Implementation status — 15 September 2026

This document describes the implemented v0.5.5 AR technology candidate (public downloads remain 0.5.0; 0.5.1 is held as a draft) (curriculum 0.4.0). Earlier architecture documents describe the target system; they are not claims that every feature is shipped.

See [current AR engineering work](ar-technology-and-validation.md) and [full problem-statement coverage](ar-problem-statement-coverage.md) for implemented changes and remaining acceptance gates.

**AR status:** physical camera feed confirmed, usable tracking/placement not confirmed. The screen fire timing investigation and current camera limits are recorded in [ar-rendering-and-placement.md](ar-rendering-and-placement.md).

## Implemented

- Native Kotlin Android application, Android 10+ minimum, fixed light theme, English and Hindi text.
- Five bundled modules: fire response, gas/confined-space decisions, machinery/isolation, PPE/exposure and emergency/reporting. Each has learning content, guided practice and eight assessment decisions.
- Native ARCore camera rendering with plane placement and pose-anchored 3D equipment and action cards; on-screen fallback and an offline orbit/zoom equipment viewer for unsupported phones. The original scored AR flow uses decision stations; the new separate procedural draft changes equipment state. Neither performs live hazard detection.
- AR decision stations use lifecycle/question revisions, fresh-image and visible-callout gates to reject stale or duplicate choices. Saved guided feedback survives recreation; assessment acknowledgements reveal no hint and require explicit Continue. Permission-off screens retain the light theme and a screen alternative. See ar-assessment-recovery.md.
- Equipment inspection uses scrollable content and native rotation/tilt/zoom controls with readable view state. Scoped headings, pane titles, distinct review labels and earlier description access support assistive services. Saved component feedback can advance without tracking; new camera choices remain gated. See accessibility.md for the exact tested subset.
- Native English/Hindi centre-placement actions share tracked-plane hit testing with direct taps. A visible camera cross, expiring viewport/revision requests, preserved anchors after missed hits and scrollable decision controls improve placement access. Positive physical-camera placement remains unverified; see ar-placement.md.
- Original inspectable illustrations for all modules, with detailed explanations in learning/practice and neutral visuals during assessment.
- Staged component recognition using the shared equipment meshes: 15 parts, guided examples, unlabeled identification, changed views, hints, text alternatives and separately attributed local practice records. Due review rounds start without examples; early clean repeats preserve the existing review date. Fire additionally supports optional camera placement and part callouts, with fresh-image/tracking gates, pause/retry handling and persistent screen/camera/mixed attribution. See camera-component-practice.md; positive physical-camera behavior remains unverified.
- Local retrieval review: missed and delayed correct decisions, explanatory feedback, self-explanation prompts and persisted spaced returns. Unchanged archived questions preserve their review schedule across curriculum updates. These learning records do not alter assessment or credential evidence.
- Critical safety mistakes stop an assessment. Every answer is written to SQLite; unfinished attempts can resume. A session resumed on screen after AR is labelled hybrid.
- Local training history, installed offline Android voices where available, PDF completion receipts with QR, JSON export for a trainer.
- Signed pilot credentials: ES256 verification on Android with a bundled trust anchor, saved credential wallet, QR and text-file import. Offline verification explicitly reports unknown revocation status.
- React trainer dashboard, D1 persistence, owner-scoped routes, import validation and score replay, worker sectors, directory filters/history, latest-assessment analytics, CSV export, critical-decision follow-up, decision review, pilot issuance, credential QR/download, signature checking and revocation.

- Fire/gas room AR missions with three separately placed stations, continuous virtual fire aim/sweep, worsening-condition withdrawal, barrier deployment, outside attendant, scoped local audit and distinct screen fallback. See room-ar-missions.md. Physical AR validation is still pending.
- Dedicated camera procedure workspace, direct-touch spatial targeting, optional centre aiming, explicit-only repositioning, fixed continuation/fallback controls and ARCore-specific recovery hints. The candidate is under validation after user feedback; see camera-training-workspace.md.
- Five spatial target-hold steps (four fire locations and gas attendant positioning) use model-space ray targeting, hold samples, interruption resets and separately attributed camera/screen evidence. The screen interaction is tested; positive physical-camera behavior is pending. These are discrete target holds, not continuous sweep or real equipment handling. See spatial-procedure-practice.md.
- Ordered fire (12 steps) and gas (9 steps) procedures with guided retry/independent stop, worker-scoped replayable journals, camera/screen/text attribution and equipment-state variants. See procedure-training.md. These drafts are not practical skill measurements or credential evidence.
- Shared-phone learner profiles, schema-4 migration preserving earlier payloads, separate histories/recalls/component records and credential wallets. Old learner screens retire on profile change, saved state is owner-bound and pending exports use separate request files. No PIN, remote authentication or identity assurance is claimed.
- Signed explicit certificate expiry, fresh validity checks, legacy no-expiry status and renewal requiring a new passed assessment. Signature authenticity and present validity are separate.
- Mineral Light action roles: teal primary actions, blue learning, violet camera/3D, amber review/caution and red destructive actions. Native ripples/focus/disabled states and dashboard hover/pressed/reduced-motion support.

## Architecture choices in this build

Native Kotlin/ARCore replaces the initially proposed Unity integration for the first build. The app uses native Android views and SQLiteOpenHelper. The dashboard uses the Sites React/Vinext starter with D1. Android-to-trainer transfer uses exported JSON files, not automatic network sync. Issuer secrets stay in ignored local runtime configuration; only the public trust anchor is in source.

## Required before field or competition-complete release

1. Test real ARCore phones, camera tracking, recovery, thermal performance, and Android 10 hardware. Emulator testing does not verify real AR performance.
2. Commission and review Santali text/Ol Chiki fonts and native audio. The language selector shows review pending instead of fabricated translations.
3. Have a competent industrial safety reviewer approve scenarios, thresholds, terms and practical assessment policy. All supplied learning content is pilot draft.
4. Extend the new labelled-action procedures into validated equipment manipulation/spatial scoring, add facilitator-controlled practical assessment, identity/organisation roles and tamper-resistant sync.
5. Review site-specific emergency contacts, routes and responder roles; the new emergency domain is a general pilot, not first-aid/rescue qualification. The original brief was truncated, so this domain remains a product proposal.
6. Complete hosting and secure issuer provisioning; support revocation freshness and key rotation. The current dashboard is local.
7. Complete field evaluation, accessibility testing with target workers, production APK signing/distribution and an end-to-end real-device AR demo.

No successful physical-phone AR completion, reviewed Santali, statutory certification or production compliance is claimed.

Validation evidence and limitations are recorded in [validation.md](validation.md). The supplied MP4 is an emulator walkthrough, not a field or AR camera demonstration.
