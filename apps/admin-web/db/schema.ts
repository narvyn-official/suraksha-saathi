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
  (t) => [primaryKey({ columns: [t.owner, t.id] }), index("idx_attempts_worker").on(t.owner,t.workerId)],
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
  owner: text("owner").notNull(), email: text("email").notNull(), userId: text("user_id"), role: text("role").notNull(), active: integer("active").notNull(), updatedAt: integer("updated_at").notNull(),
}, t => [primaryKey({ columns: [t.owner, t.email] }), uniqueIndex("idx_team_user").on(t.owner,t.userId)]);
export const trainingAssignments = sqliteTable("training_assignments", {
  owner: text("owner").notNull(), id: text("id").notNull(), workerId: text("worker_id").notNull(), moduleId: text("module_id").notNull(), contentVersion: text("content_version").notNull().default("0.4.0"), dueAt: integer("due_at").notNull(), createdAt: integer("created_at").notNull(), createdBy: text("created_by").notNull(), note: text("note").notNull(), cancelledAt: integer("cancelled_at"),
}, t => [primaryKey({ columns: [t.owner, t.id] })]);
export const auditLog = sqliteTable("audit_log", {
  id: text("id").primaryKey(), owner: text("owner").notNull(), actor: text("actor").notNull(), actorEmail: text("actor_email").notNull(), action: text("action").notNull(), target: text("target").notNull(), detail: text("detail").notNull(), at: integer("at").notNull(),
}, t => [index("idx_audit_owner_time").on(t.owner,t.at)]);
