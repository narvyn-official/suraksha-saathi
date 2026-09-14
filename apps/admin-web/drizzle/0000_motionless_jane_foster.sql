CREATE TABLE `attempts` (
	`owner` text NOT NULL,
	`id` text NOT NULL,
	`worker_id` text NOT NULL,
	`worker_name` text NOT NULL,
	`payload` text NOT NULL,
	`digest` text NOT NULL,
	`imported_at` integer NOT NULL,
	PRIMARY KEY(`owner`, `id`)
);
--> statement-breakpoint
CREATE TABLE `credentials` (
	`owner` text NOT NULL,
	`id` text NOT NULL,
	`attempt_id` text NOT NULL,
	`token` text NOT NULL,
	`issued_at` integer NOT NULL,
	`revoked_at` integer,
	`reason` text,
	PRIMARY KEY(`owner`, `id`)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_credentials_owner_attempt` ON `credentials` (`owner`,`attempt_id`);