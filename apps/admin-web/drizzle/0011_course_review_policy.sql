-- Existing requests retain their original knowledge-only scope. New requests use policy 2.
ALTER TABLE certification_requests ADD COLUMN workflow_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE certification_requests ADD COLUMN learning_digest TEXT;
