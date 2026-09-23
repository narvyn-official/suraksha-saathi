ALTER TABLE auth_two_factor ADD COLUMN failed_verification_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE auth_two_factor ADD COLUMN locked_until INTEGER;
