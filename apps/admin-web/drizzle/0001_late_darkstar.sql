CREATE TABLE `workers` (
	`owner` text NOT NULL,
	`id` text NOT NULL,
	`name` text NOT NULL,
	`sector` text NOT NULL,
	`updated_at` integer NOT NULL,
	PRIMARY KEY(`owner`, `id`)
);
