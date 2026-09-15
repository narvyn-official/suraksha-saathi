CREATE TABLE `room_journal_heads` (
	`owner` text NOT NULL,
	`id` text NOT NULL,
	`worker_id` text NOT NULL,
	`worker_name` text NOT NULL,
	`worker_sector` text NOT NULL,
	`digest` text NOT NULL,
	`captured_at` integer NOT NULL,
	`payload` text NOT NULL,
	`imported_at` integer NOT NULL,
	PRIMARY KEY(`owner`, `id`)
);
--> statement-breakpoint
CREATE TABLE `room_journal_snapshots` (
	`owner` text NOT NULL,
	`id` text NOT NULL,
	`digest` text NOT NULL,
	`previous_digest` text,
	`captured_at` integer NOT NULL,
	`imported_at` integer NOT NULL,
	`payload` text NOT NULL,
	PRIMARY KEY(`owner`, `id`, `digest`)
);

--> statement-breakpoint
CREATE TRIGGER room_snapshot_parent BEFORE INSERT ON room_journal_snapshots
WHEN (SELECT digest FROM room_journal_heads WHERE owner=NEW.owner AND id=NEW.id) IS NOT NEW.previous_digest
BEGIN SELECT RAISE(ABORT,'Record conflict: room snapshot changed concurrently. Retry import.'); END;
--> statement-breakpoint
CREATE TRIGGER room_snapshot_capacity BEFORE INSERT ON room_journal_snapshots
WHEN (SELECT COUNT(*) FROM room_journal_snapshots WHERE owner=NEW.owner)>=10000
 OR (NEW.previous_digest IS NULL AND (SELECT COUNT(*) FROM room_journal_heads WHERE owner=NEW.owner)>=2000)
BEGIN SELECT RAISE(ABORT,'Invalid room journal: workspace practice archive is full.'); END;
