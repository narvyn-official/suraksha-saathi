CREATE INDEX `idx_attempts_worker` ON `attempts` (`owner`,`worker_id`);--> statement-breakpoint
CREATE INDEX `idx_audit_owner_time` ON `audit_log` (`owner`,`at`);--> statement-breakpoint
CREATE UNIQUE INDEX `idx_team_user` ON `team_members` (`owner`,`user_id`);