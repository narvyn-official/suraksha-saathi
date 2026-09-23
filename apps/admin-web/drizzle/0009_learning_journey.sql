-- Additive migration: retained assessments and signed credentials are unchanged.
CREATE TABLE learner_links (owner TEXT NOT NULL, worker_id TEXT NOT NULL, user_id TEXT, email TEXT NOT NULL, invite_hash TEXT, invite_expires_at INTEGER, linked_at INTEGER, PRIMARY KEY(owner,worker_id), UNIQUE(owner,user_id));
CREATE INDEX learner_user ON learner_links(user_id);
CREATE UNIQUE INDEX learner_invite ON learner_links(invite_hash);
CREATE TABLE learning_snapshots (owner TEXT NOT NULL, worker_id TEXT NOT NULL, device_id TEXT NOT NULL, revision INTEGER NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(owner,worker_id,device_id));
CREATE TABLE procedure_evidence (owner TEXT NOT NULL, id TEXT NOT NULL, worker_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(owner,id));
CREATE TABLE certification_events (id TEXT PRIMARY KEY, request_id TEXT NOT NULL, owner TEXT NOT NULL, actor TEXT NOT NULL, action TEXT NOT NULL, note TEXT NOT NULL, at INTEGER NOT NULL);
CREATE INDEX certification_timeline ON certification_events(owner,request_id,at);
CREATE TABLE certification_requests_next (
 id TEXT PRIMARY KEY NOT NULL, owner TEXT NOT NULL, attempt_id TEXT NOT NULL, evidence_digest TEXT NOT NULL, expires_at INTEGER NOT NULL,
 requested_by TEXT NOT NULL, requested_at INTEGER NOT NULL, request_note TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('pending','needs-information','approved','rejected')),
 reviewed_by TEXT, reviewed_at INTEGER, review_reason TEXT, credential_id TEXT, rubric TEXT, UNIQUE(owner,attempt_id));
INSERT INTO certification_requests_next SELECT *,NULL FROM certification_requests;
DROP TABLE certification_requests;
ALTER TABLE certification_requests_next RENAME TO certification_requests;
CREATE INDEX idx_certification_queue ON certification_requests(owner,status,requested_at);
CREATE TABLE practical_observations (id TEXT PRIMARY KEY, owner TEXT NOT NULL, worker_id TEXT NOT NULL, module_id TEXT NOT NULL, instructor_id TEXT NOT NULL, observed_at INTEGER NOT NULL, rubric TEXT NOT NULL, outcome TEXT NOT NULL, note TEXT NOT NULL, created_at INTEGER NOT NULL);
ALTER TABLE auth_user ADD COLUMN two_factor_enabled INTEGER NOT NULL DEFAULT 0;
CREATE TABLE auth_two_factor (id TEXT PRIMARY KEY, secret TEXT NOT NULL, backup_codes TEXT NOT NULL, user_id TEXT NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE, verified INTEGER DEFAULT 1);
CREATE INDEX auth_two_factor_user ON auth_two_factor(user_id);

-- Protect compare-and-swap uploads under retries/concurrent devices.
CREATE TRIGGER procedure_history_guard BEFORE UPDATE ON procedure_evidence WHEN
 NEW.worker_id<>OLD.worker_id OR json_extract(NEW.payload,'$.createdAt')<>json_extract(OLD.payload,'$.createdAt') OR
 json_extract(NEW.payload,'$.module')<>json_extract(OLD.payload,'$.module') OR json_extract(NEW.payload,'$.guided')<>json_extract(OLD.payload,'$.guided') OR
 json_extract(NEW.payload,'$.catalogVersion')<>json_extract(OLD.payload,'$.catalogVersion') OR
 json_array_length(NEW.payload,'$.events')<json_array_length(OLD.payload,'$.events') OR
 EXISTS(SELECT 1 FROM json_each(OLD.payload,'$.events') e WHERE json_extract(NEW.payload,'$.events['||e.key||']')<>e.value)
 BEGIN SELECT RAISE(ABORT,'Record conflict: procedure history changed'); END;
CREATE TRIGGER snapshot_revision_guard BEFORE UPDATE ON learning_snapshots WHEN NEW.revision<OLD.revision OR (NEW.revision=OLD.revision AND NEW.payload<>OLD.payload)
 BEGIN SELECT RAISE(ABORT,'Record conflict: learning revision changed'); END;
