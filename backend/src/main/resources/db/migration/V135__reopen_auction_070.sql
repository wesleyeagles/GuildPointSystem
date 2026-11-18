-- One-time correction requested after the legacy deadline adjustment stopped early.
DO $repair$
DECLARE
    target_id constant bigint := 70;
    repair_end constant timestamptz := '2026-11-18 21:50:00 America/Sao_Paulo';
    lot auction%ROWTYPE;
    close_payload jsonb;
    refund_amount bigint;
BEGIN
    SELECT * INTO lot FROM auction WHERE id = target_id FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    -- Other installations may use the same numeric id for an unrelated auction.
    IF lot.created_at NOT BETWEEN '2026-11-17 23:27:00 America/Sao_Paulo'::timestamptz
                              AND '2026-11-17 23:29:00 America/Sao_Paulo'::timestamptz
       OR NOT EXISTS (
           SELECT 1 FROM auction_item ai JOIN item i ON i.id = ai.item_id
           WHERE ai.auction_id = target_id AND i.name = '(1GR) Heart of Caves Relic'
       ) THEN RETURN;
    END IF;
    IF EXISTS (
        SELECT 1 FROM audit_log WHERE payload->>'repair' = 'reopen-auction-070-20261118'
    ) THEN RETURN; END IF;
    IF lot.status <> 'CLOSED' OR lot.delivered_at IS NOT NULL OR lot.current_bid <> 90
       OR lot.winner_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM member WHERE id = lot.winner_id AND nickname = 'CKP')
       OR repair_end <= clock_timestamp() THEN
        RAISE EXCEPTION 'Auction 070 changed or the requested deadline passed. Review before reopening.';
    END IF;
    SELECT payload INTO close_payload FROM audit_log
    WHERE type = 'AUCTION_CLOSED' AND payload->>'auctionId' = target_id::text
    ORDER BY id DESC LIMIT 1;
    IF close_payload IS NULL OR (close_payload->>'winnerId')::bigint IS DISTINCT FROM lot.winner_id
       OR COALESCE((close_payload->>'charged')::bigint, 0) <> 90 THEN
        RAISE EXCEPTION 'Auction 070: original charge could not be confirmed.';
    END IF;
    refund_amount := (close_payload->>'charged')::bigint;
    PERFORM id FROM member WHERE id = lot.winner_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM points_adjustment
        WHERE member_id = lot.winner_id AND modality = 'LEILAO'
          AND reason = 'Auction win #' || target_id AND amount = -refund_amount
    ) THEN
        RAISE EXCEPTION 'Auction 070: original debit is missing.';
    END IF;
    UPDATE member SET points = points + refund_amount WHERE id = lot.winner_id;
    INSERT INTO points_adjustment(member_id, actor_id, amount, modality, reason)
    VALUES (lot.winner_id, lot.created_by_id, refund_amount, 'REVERSAO',
            'Estorno do encerramento antecipado do leilão #070; reaberto até 18/11/2026 21h50 de Brasília.');
    UPDATE auction SET status = 'OPEN', ends_at = repair_end, winner_id = NULL,
        fixed_deadline = false, paused_remaining_ms = NULL, dole_phase = 0,
        tie_break_phase = 0, tie_break_seed = NULL, state_version = state_version + 1
    WHERE id = target_id;
    INSERT INTO audit_log(type, actor_id, target_id, payload)
    VALUES ('AUCTION_CREATED', lot.created_by_id, lot.winner_id,
        jsonb_build_object('auctionId', target_id, 'action', 'REOPEN',
            'repair', 'reopen-auction-070-20261118', 'refunded', refund_amount,
            'endsAt', repair_end, 'previousEndsAt', lot.ends_at,
            'reason', 'Correção do encerramento antecipado; lances preservados.'));
    INSERT INTO auction_message(auction_id, type, content)
    VALUES (target_id, 'BOT',
        'Leilão reaberto por encerramento antecipado. Os lances foram preservados e 90 pontos foram estornados ao CKP. Novo encerramento: 18/11/2026 às 21h50 de Brasília.');
END
$repair$;
