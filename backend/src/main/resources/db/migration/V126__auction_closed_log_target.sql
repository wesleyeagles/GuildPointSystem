-- "Leilão encerrado" passa a ter o vencedor como membro do log (antes ficava sem): corrige os antigos.
UPDATE audit_log a
SET target_id = m.id
FROM member m
WHERE a.type = 'AUCTION_CLOSED'
  AND a.target_id IS NULL
  AND a.payload ->> 'winnerId' ~ '^[0-9]+$'
  AND m.id = (a.payload ->> 'winnerId')::bigint;
