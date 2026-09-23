import { sql } from "drizzle-orm";
import {
  sqliteTable,
  text,
  integer,
  primaryKey,
  index,
  uniqueIndex,
} from "drizzle-orm/sqlite-core";
export const attempts = sqliteTable(
  "attempts",
  {
    owner: text("owner").notNull(),
    id: text("id").notNull(),
    workerId: text("worker_id").notNull(),
    workerName: text("worker_name").notNull(),
    payload: text("payload").notNull(),
    digest: text("digest").notNull(),
    importedAt: integer("imported_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.owner, t.id] }), index("idx_attempts_worker").on(t.owner,t.workerId), index("idx_attempts_latest_assessment").on(t.owner,t.workerId,sql`json_extract(${t.payload},'$.moduleId')`,sql`json_extract(${t.payload},'$.kind')`,sql`json_extract(${t.payload},'$.endedAt') DESC`,sql`${t.id} DESC`)],
);
export const credentials = sqliteTable(
  "credentials",
  {
    owner: text("owner").notNull(),
    id: text("id").notNull(),
    attemptId: text("attempt_id").notNull(),
    token: text("token").notNull(),
    issuedAt: integer("issued_at").notNull(),
    revokedAt: integer("revoked_at"),
    reason: text("reason"),
  },
  (t) => [
    primaryKey({ columns: [t.owner, t.id] }),
    uniqueIndex("idx_credentials_owner_attempt").on(t.owner, t.attemptId),
  ],
);
export const workers = sqliteTable(
  "workers",
  {
    owner: text("owner").notNull(),
    id: text("id").notNull(),
    name: text("name").notNull(),
    sector: text("sector").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.owner, t.id] })],
);

/** Practice journals are stored separately and never referenced by credential issuance. */
export const roomJournalHeads = sqliteTable("room_journal_heads", {
  owner: text("owner").notNull(), id: text("id").notNull(), workerId: text("worker_id").notNull(),
  workerName: text("worker_name").notNull(), workerSector: text("worker_sector").notNull(),
  digest: text("digest").notNull(), capturedAt: integer("captured_at").notNull(), payload: text("payload").notNull(), importedAt: integer("imported_at").notNull(),
}, (t) => [primaryKey({ columns: [t.owner, t.id] })]);
export const roomJournalSnapshots = sqliteTable("room_journal_snapshots", {
  owner: text("owner").notNull(), id: text("id").notNull(), digest: text("digest").notNull(),
  previousDigest: text("previous_digest"), capturedAt: integer("captured_at").notNull(), importedAt: integer("imported_at").notNull(), payload: text("payload").notNull(),
}, (t) => [primaryKey({ columns: [t.owner, t.id, t.digest] })]);

export const trainingCentres = sqliteTable("training_centres", {
  owner: text("owner").primaryKey(), name: text("name").notNull(), site: text("site").notNull(), updatedAt: integer("updated_at").notNull(),
});
export const teamMembers = sqliteTable("team_members", {
  owner: text("owner").notNull(), email: text("email").notNull(), userId: text("user_id"), inviteHash: text("invite_hash"), inviteExpiresAt: integer("invite_expires_at"), role: text("role").notNull(), active: integer("active").notNull(), updatedAt: integer("updated_at").notNull(),
}, t => [primaryKey({ columns: [t.owner, t.email] }), uniqueIndex("idx_team_user").on(t.owner,t.userId)]);
export const trainingAssignments = sqliteTable("training_assignments", {
  owner: text("owner").notNull(), id: text("id").notNull(), workerId: text("worker_id").notNull(), moduleId: text("module_id").notNull(), contentVersion: text("content_version").notNull().default("0.4.0"), dueAt: integer("due_at").notNull(), createdAt: integer("created_at").notNull(), createdBy: text("created_by").notNull(), note: text("note").notNull(), cancelledAt: integer("cancelled_at"),
}, t => [primaryKey({ columns: [t.owner, t.id] }),index("idx_assignments_owner_created").on(t.owner,sql`${t.createdAt} DESC`)]);
export const auditLog = sqliteTable("audit_log", {
  id: text("id").primaryKey(), owner: text("owner").notNull(), actor: text("actor").notNull(), actorEmail: text("actor_email").notNull(), action: text("action").notNull(), target: text("target").notNull(), detail: text("detail").notNull(), at: integer("at").notNull(),
}, t => [index("idx_audit_owner_time").on(t.owner,t.at)]);

export * from "./auth-schema";

export const centreApprovals=sqliteTable('centre_approvals',{
 owner:text('owner').primaryKey(),status:text('status').notNull(),requestedAt:integer('requested_at').notNull(),reviewedAt:integer('reviewed_at'),reviewedBy:text('reviewed_by'),reason:text('reason').notNull().default(''),
});
export const certificationRequests=sqliteTable('certification_requests',{
 id:text('id').primaryKey(),owner:text('owner').notNull(),attemptId:text('attempt_id').notNull(),evidenceDigest:text('evidence_digest').notNull(),expiresAt:integer('expires_at').notNull(),requestedBy:text('requested_by').notNull(),requestedAt:integer('requested_at').notNull(),requestNote:text('request_note').notNull(),status:text('status').notNull(),reviewedBy:text('reviewed_by'),reviewedAt:integer('reviewed_at'),reviewReason:text('review_reason'),credentialId:text('credential_id'),rubric:text('rubric'),workflowVersion:integer('workflow_version').notNull().default(1),learningDigest:text('learning_digest'),
},t=>[uniqueIndex('certification_requests_owner_attempt').on(t.owner,t.attemptId),index('idx_certification_queue').on(t.owner,t.status,t.requestedAt)]);

export const learnerLinks=sqliteTable('learner_links',{owner:text('owner').notNull(),workerId:text('worker_id').notNull(),userId:text('user_id'),email:text('email').notNull(),inviteHash:text('invite_hash'),inviteExpiresAt:integer('invite_expires_at'),linkedAt:integer('linked_at')},t=>[primaryKey({columns:[t.owner,t.workerId]}),uniqueIndex('learner_account_centre').on(t.owner,t.userId),uniqueIndex('learner_invite').on(t.inviteHash),index('learner_user').on(t.userId)]);
export const learningSnapshots=sqliteTable('learning_snapshots',{owner:text('owner').notNull(),workerId:text('worker_id').notNull(),deviceId:text('device_id').notNull(),revision:integer('revision').notNull(),payload:text('payload').notNull(),updatedAt:integer('updated_at').notNull()},t=>[primaryKey({columns:[t.owner,t.workerId,t.deviceId]})]);
export const procedureEvidence=sqliteTable('procedure_evidence',{owner:text('owner').notNull(),id:text('id').notNull(),workerId:text('worker_id').notNull(),payload:text('payload').notNull(),updatedAt:integer('updated_at').notNull()},t=>[primaryKey({columns:[t.owner,t.id]})]);
export const certificationEvents=sqliteTable('certification_events',{id:text('id').primaryKey(),requestId:text('request_id').notNull(),owner:text('owner').notNull(),actor:text('actor').notNull(),action:text('action').notNull(),note:text('note').notNull(),at:integer('at').notNull()},t=>[index('certification_timeline').on(t.owner,t.requestId,t.at)]);
export const practicalObservations=sqliteTable('practical_observations',{id:text('id').primaryKey(),owner:text('owner').notNull(),workerId:text('worker_id').notNull(),moduleId:text('module_id').notNull(),instructorId:text('instructor_id').notNull(),observedAt:integer('observed_at').notNull(),rubric:text('rubric').notNull(),outcome:text('outcome').notNull(),note:text('note').notNull(),createdAt:integer('created_at').notNull()});
