ALTER TABLE auction_bid
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancelled_by_id bigint REFERENCES member(id) ON DELETE SET NULL,
    ADD COLUMN cancellation_reason varchar(300);

CREATE INDEX idx_auction_bid_active_amount ON auction_bid(auction_id, amount DESC, id)
    WHERE cancelled_at IS NULL;
