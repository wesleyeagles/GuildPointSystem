-- Manual event grants (admin assigns objective points without a broadcast event)
ALTER TABLE event_claim ALTER COLUMN event_id DROP NOT NULL;

ALTER TABLE event_claim DROP CONSTRAINT IF EXISTS event_claim_event_id_member_id_key;

CREATE UNIQUE INDEX idx_event_claim_event_member
    ON event_claim (event_id, member_id)
    WHERE event_id IS NOT NULL;
