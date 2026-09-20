# SurakshaAr 0.9.0 — government demonstration and centre governance

20 September 2026. This release implements the approved-centre model chosen for the demonstration. It supports a controlled pilot review; it does not claim government adoption, accreditation, statutory certification, verified identity or permission to work. The Android package, public trust anchor, existing learning histories and signed-token format prefix remain compatible.

## What changed and why

| Problem found | Implemented behaviour |
|---|---|
| Signing up automatically granted a personal admin workspace | New accounts have no centre authority. An independently provisioned platform operator approves the centre before its owner becomes an admin. Existing centres also need an explicit review. |
| Trainers and admins could directly sign certificates | Trainers/admins submit an evidence note and expiry. A different person with the certifier role reviews, approves or rejects. Only approval signs a new credential. |
| An older pass could hide a later failure | Both request and approval replay the evidence and require the latest assessment for that learner/module to pass on the current curriculum. The atomic signing insertion rechecks latest evidence and authority. |
| Missing decision provenance | Requests and decisions retain actors, dates, notes and evidence digest. New signatures carry centre, learner reference/name, requester, approver, request reference and digest. |
| Revoked or suspended access left unclear recovery | Protected data is withheld, with centre status, workspace selection, invitation entry, refresh and account recovery available. |
| Reading position was lost between app launches | Bookmarks are isolated by learner, module and curriculum version. Finishing reading resets the bookmark; reading never creates passed-assessment evidence. |
| Unnecessary browser work and repeated database scans | Charts/worker insights and QR rendering load on demand. Latest-assessment, assignment and review-queue indexes support their queries. Clock refresh after server responses avoids briefly treating a just-issued credential as future-dated. |

The current learning library remains five domains / 40 decisions, English and Hindi. New guidance explains the path through reading, guided practice, assessment and follow-up. Existing spaced recall, corrective feedback, explanation prompts, procedure practice and interrupted-attempt recovery remain available. This release does not claim measured learning gains or newly validated safety content.

## Enforced roles

All centre capabilities require an approved centre and a current membership lookup on the server. Hiding a button is not the permission boundary.

| Capability | Viewer | Trainer | Centre admin/owner | Certifier | Platform operator |
|---|---|---|---|---|---|
| Read centre records and audit | Yes | Yes | Yes | Yes | Only with a separate centre membership |
| Workers, assignments and evidence imports | No | Yes | Yes | No | No inherent access |
| Request certification review | No | Yes | Yes | No | No inherent access |
| Approve/reject requests | No | No | No | Yes, except own request | No inherent access |
| Revoke a signed pilot record | No | No | Yes | Yes | No inherent access |
| Invite/revoke staff; edit centre settings | No | No | Yes | No | No inherent access |
| Approve/reject/suspend/restore centres | No | No | No | No | Yes, except own centre |

Offline learner profiles are a separate device feature. A learner profile is not an authenticated staff account. Private invitations require the matching account email and one-time code; sign-up alone does not prove ownership of an email address. A centre admin appoints its certifiers, so this model provides separate requester/reviewer accounts, not an external professional-accreditation authority. A person cannot approve their own request even after changing role.

Centre review, staff grants/revocation, credential approval/rejection and credential revocation require a session created within the preceding 15 minutes. Sign out and sign in again when prompted. This is recent authentication, not MFA.

## First setup and migration

1. Preserve the database, issuer key and matching Android/web public trust files. Do not regenerate an existing issuer to perform this upgrade.
2. Follow the main README's local auth/issuer setup if this is a fresh clone. Stop the service before taking a local SQLite backup. For hosted D1 use its platform backup/export procedure and verify restore in staging before migration.
3. From `apps/admin-web`, apply all migrations through `0008_governance.sql`:

   ```sh
   npx wrangler d1 migrations apply DB --local --config wrangler.local.json --persist-to .wrangler/state
   ```

4. Start the service and create a dedicated operator account and a different centre-owner account. Account creation does not grant authority. Obtain the exact operator `auth_user.id` using the local database or the signed-in `/api/admin/session` response; do not use an email address as the operator ID.
5. Stop the local service, then provision only that intended account:

   ```sh
   node scripts/operator-local.mjs add EXACT_AUTH_USER_ID
   npm run dev
   ```

   This validates the account exists and updates only local ignored `.dev.vars`. It never approves a centre. Remove a local operator with `node scripts/operator-local.mjs remove EXACT_AUTH_USER_ID` and restart. It preserves other configured IDs.
6. Sign in as the centre owner, enter centre name/site under **Centre approval**, and submit. Sign in separately as the operator, review that application and record a decision/reason. An operator cannot approve its own application. Suspended centres can be restored after an operator records a reason; rejected applicants can correct and resubmit.
7. Once approved, the owner invites a trainer, a separate certifier and optionally a viewer under **Team access**. Deliver invitation codes privately through the institution's authorized channel; the app does not automatically email them. Each invited person signs in with the matching address and uses **Account menu → Join a centre**.

For a future hosted service, `PLATFORM_OPERATOR_IDS` must be provisioned as backend configuration by the deployment operator, using exact account IDs from that environment. The local script does not deploy, configure remote authority or access another system. An empty list denies all operator actions. Public hosting, production operator identity and recovery delivery have not been provisioned in this release.

**Existing data:** migration 0008 is additive. It does not delete histories, approve every existing owner or rewrite tokens. Existing owners lose centre access until explicitly approved; plan that change and notify affected pilot operators before a hosted rollout. Legacy credentials remain signature-verifiable and labelled as lacking independent review metadata. Rejected requests cannot be renewed in place: retain the decision and create a new eligible assessment. Issued/revoked records cannot have their expiry extended. Requested pilot validity is limited to at most 366 days; this is an application policy, not a statutory validity rule.

**Rollback:** do not roll back only the application code to 0.8.x: that would restore direct signing/self-service access. Keep 0.9.0 and suspend affected centres while investigating, or take the pilot service offline and restore a tested database/application backup in an isolated environment. Never delete evidence to make an upgrade pass.

## Eight-minute demonstration

Use labelled synthetic learners and separate accounts. Keep real identity documents, Aadhaar and personal employment/medical information out of the demonstration.

1. **Purpose (45 sec):** show SurakshaAr, the five domains, supported languages and offline use. Explain that it records learning decisions and helps trainers identify gaps.
2. **Learning (90 sec):** open a lesson, move forward, restart and resume. Show guided feedback and the next learning step. Explain that reading is not certification.
3. **Practice (90 sec):** demonstrate fire/gas procedure practice. Use screen mode on an emulator. Show physical AR only on a supported, tested phone and label the mode honestly.
4. **Evidence (60 sec):** export/import a synthetic assessment, open its individual answers, and show a critical error requiring retraining. An old pass must not hide a newer failure.
5. **Governance (60 sec):** show a new account without admin rights, the reviewed centre, staff roles and a denied trainer signing action.
6. **Independent review (90 sec):** trainer requests review with a note/expiry; a different certifier examines answers and approves. Show pending → approved, the audit entry and QR. A rejected request retains its reason.
7. **Verification and limits (45 sec):** verify the signature, revoke online, and explain that offline verification cannot know current revocation. Close with practical assessment and field-validation requirements.

A short explanation to use: “The phone records training events offline. The server validates and replays those events to compute the result. An approved centre submits the latest eligible assessment for independent review. Only a separate certifier's approval creates a signed pilot learning credential. It is evidence of simulation learning, not proof of identity, practical competence or authorization to work.”

## Verification and release gates

See [the release evidence](release-0.9.0.md) for actual checks and limits. Test helpers may explicitly seed approved **local synthetic fixtures**; that capability is not an application API, header or production signup shortcut. The governance suite tests real application/approval HTTP paths.

For repeatable local governance QA, with migrations and the dev server running:

```sh
node tests/setup-governance-qa.mjs
# Restart the dev server to load the synthetic operator ID.
npx tsx tests/governance.integration.ts
```

Setup refuses to overwrite an existing `.sites-runtime/governance-qa.json`; reuse it for reruns. Credentials remain ignored, local and synthetic. The suite resets only those accounts' centre fixtures. After testing, remove only its `operatorAdded` ID using the local operator script and restart; delete that ignored fixture file only when its credentials are no longer needed. Never run test fixtures against a real deployment.

Before government field use: appoint accountable deployment/centre/assessor operators, establish identity and assessor qualification requirements, add institution-approved MFA/SSO, provision and test HTTPS hosting/recovery/monitoring/backups, review retention/export/deletion and QR disclosure (new QR payloads include the learner name), complete safety-content and practical-assessment review, validate languages and physical AR on the intended device range, and perform formal accessibility/security assessment. Current review lists are bounded to 200 centre applications and 500 certification requests; national/state-scale workloads require pagination, archival policy and load testing before expansion. Existing latest-500 record analytics disclose their coverage.

GIGW guidance informs these requirements; passing this project's tests does not establish compliance. Primary guidance reviewed: [GIGW 3.0 features](https://guidelines.india.gov.in/new-features-of-gigw-3-0/), [GIGW scope and objective](https://guidelines.india.gov.in/scope-and-objective/) and [STQC website quality certification](https://stqc.gov.in/website-quality-certification-0). No government emblem, certification mark or endorsement was added.
