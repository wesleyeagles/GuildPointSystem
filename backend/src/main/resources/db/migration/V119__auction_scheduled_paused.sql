-- Auctions created without starting (SCHEDULED: at starts_at, or by hand when it is NULL = "em breve")
-- and paused auctions (PAUSED: clock frozen with paused_remaining_ms left).

ALTER TABLE auction ADD COLUMN IF NOT EXISTS starts_at TIMESTAMPTZ;
ALTER TABLE auction ADD COLUMN IF NOT EXISTS paused_remaining_ms BIGINT;

ALTER TABLE auction DROP CONSTRAINT IF EXISTS chk_auction_status;
ALTER TABLE auction ADD CONSTRAINT chk_auction_status
    CHECK (status IN ('SCHEDULED', 'OPEN', 'DOLE', 'TIE_BREAK', 'PAUSED', 'CLOSED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_auction_scheduled_start ON auction (starts_at) WHERE status = 'SCHEDULED';
