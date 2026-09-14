# Implementation status — 14 September 2026

This document describes the implemented v0.3 pilot. Earlier architecture documents describe the target system; they are not claims that every feature is shipped.

## Implemented

- Native Kotlin Android application, Android 10+ minimum, fixed light theme, English and Hindi text.
- Four bundled modules: fire response, gas/confined-space decisions, machinery/isolation and PPE/exposure. Each has learning content, guided practice and eight assessment decisions.
- Native ARCore camera rendering with plane placement and pose-anchored 3D equipment and action cards; on-screen fallback and an offline orbit/zoom equipment viewer for unsupported phones. These are decision stations, not animated equipment manipulation or live hazard detection.
- Original inspectable illustrations for all modules, with detailed explanations in learning/practice and neutral visuals during assessment.
- Local retrieval review: missed and delayed correct decisions, explanatory feedback, self-explanation prompts and persisted spaced returns. These learning records do not alter assessment or credential evidence.
- Critical safety mistakes stop an assessment. Every answer is written to SQLite; unfinished attempts can resume. A session resumed on screen after AR is labelled hybrid.
- Local training history, installed offline Android voices where available, PDF completion receipts with QR, JSON export for a trainer.
- Signed pilot credentials: ES256 verification on Android with a bundled trust anchor, saved credential wallet, QR and text-file import. Offline verification explicitly reports unknown revocation status.
- React trainer dashboard, D1 persistence, owner-scoped routes, import validation and score replay, worker sectors, directory filters/history, latest-assessment analytics, CSV export, critical-decision follow-up, decision review, pilot issuance, credential QR/download, signature checking and revocation.

## Architecture choices in this build

Native Kotlin/ARCore replaces the initially proposed Unity integration for the first build. The app uses native Android views and SQLiteOpenHelper. The dashboard uses the Sites React/Vinext starter with D1. Android-to-trainer transfer uses exported JSON files, not automatic network sync. Issuer secrets stay in ignored local runtime configuration; only the public trust anchor is in source.

## Required before field or competition-complete release

1. Test real ARCore phones, camera tracking, recovery, thermal performance, and Android 10 hardware. Emulator testing does not verify real AR performance.
2. Commission and review Santali text/Ol Chiki fonts and native audio. The language selector shows review pending instead of fabricated translations.
3. Have a competent industrial safety reviewer approve scenarios, thresholds, terms and practical assessment policy. All supplied learning content is pilot draft.
4. Add tactile/animated AR tasks, spatial scoring, facilitator-controlled practical assessment, identity/organisation roles and tamper-resistant sync.
5. Add the proposed emergency-response domain, keeping untrained-worker guidance separate from practical rescue/first-aid qualification. The original brief was truncated; this fifth domain is a product proposal.
6. Complete hosting and secure issuer provisioning; support revocation freshness and key rotation. The current dashboard is local.
7. Complete field evaluation, accessibility testing with target workers, production APK signing/distribution and an end-to-end real-device AR demo.

No physical-phone AR test, reviewed Santali, statutory certification or production compliance is claimed.

Validation evidence and limitations are recorded in [validation.md](validation.md). The supplied MP4 is an emulator walkthrough, not a field or AR camera demonstration.
