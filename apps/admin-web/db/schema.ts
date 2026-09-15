import {
  sqliteTable,
  text,
  integer,
  primaryKey,
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
  (t) => [primaryKey({ columns: [t.owner, t.id] })],
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
