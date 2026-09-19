-- Identical retries are safe; conflicting content must abort the entire D1 batch.
CREATE TRIGGER assessment_import_guard BEFORE INSERT ON attempts
WHEN EXISTS (SELECT 1 FROM attempts WHERE owner=NEW.owner AND id=NEW.id AND digest<>NEW.digest)
BEGIN SELECT RAISE(ABORT,'Record conflict: an existing attempt has different answers.'); END;
