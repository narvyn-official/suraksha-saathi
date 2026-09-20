CREATE TABLE centre_approvals (
 owner TEXT PRIMARY KEY NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('pending','approved','rejected','suspended')),
 requested_at INTEGER NOT NULL,
 reviewed_at INTEGER,
 reviewed_by TEXT,
 reason TEXT NOT NULL DEFAULT ''
);
CREATE TABLE certification_requests (
 id TEXT PRIMARY KEY NOT NULL, owner TEXT NOT NULL, attempt_id TEXT NOT NULL,
 evidence_digest TEXT NOT NULL, expires_at INTEGER NOT NULL,
 requested_by TEXT NOT NULL, requested_at INTEGER NOT NULL, request_note TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('pending','approved','rejected')),
 reviewed_by TEXT, reviewed_at INTEGER, review_reason TEXT, credential_id TEXT,
 UNIQUE(owner,attempt_id)
);
CREATE INDEX idx_certification_queue ON certification_requests(owner,status,requested_at);
CREATE INDEX idx_attempts_latest_assessment ON attempts(owner,worker_id,json_extract(payload,'$.moduleId'),json_extract(payload,'$.kind'),json_extract(payload,'$.endedAt') DESC,id DESC);
CREATE INDEX idx_assignments_owner_created ON training_assignments(owner,created_at DESC);
