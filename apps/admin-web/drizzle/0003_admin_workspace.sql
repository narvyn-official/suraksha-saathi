CREATE TABLE `audit_log` (
	`id` text PRIMARY KEY NOT NULL,
	`owner` text NOT NULL,
	`actor` text NOT NULL,
	`actor_email` text NOT NULL,
	`action` text NOT NULL,
	`target` text NOT NULL,
	`detail` text NOT NULL,
	`at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `team_members` (
	`owner` text NOT NULL,
	`email` text NOT NULL,
	`user_id` text,
	`role` text NOT NULL,
	`active` integer NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`owner`, `email`)
);
--> statement-breakpoint
CREATE TABLE `training_assignments` (
	`owner` text NOT NULL,
	`id` text NOT NULL,
	`worker_id` text NOT NULL,
	`module_id` text NOT NULL,
	`due_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	`created_by` text NOT NULL,
	`note` text NOT NULL,
	`cancelled_at` integer,
	PRIMARY KEY(`owner`, `id`)
);
--> statement-breakpoint
CREATE TABLE `training_centres` (
	`owner` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`site` text NOT NULL,
	`updated_at` integer NOT NULL
);
