-- Pontos de RG ja dados: entram no filtro "Evento" dos logs como "RG".
-- POINTS_ADJUSTED com "Pontos por local:" so vem do "Pontos de RG" (a fila de PT grava PARTY_QUEUE_UPDATED);
-- os com motivo escrito a mao que entraram no rg_award (30 min antes da CW) tambem.
UPDATE audit_log a
SET payload = a.payload || '{"objectiveName":"RG","source":"RG"}'::jsonb
WHERE a.type = 'POINTS_ADJUSTED'
  AND a.payload->>'objectiveName' IS NULL
  AND a.payload->>'modality' = 'AJUSTE'
  AND (a.payload->>'reason' LIKE 'Pontos por local:%'
       OR EXISTS (SELECT 1 FROM rg_award r
                  WHERE r.member_id = a.target_id AND r.actor_id = a.actor_id
                    AND abs(extract(epoch FROM r.at - a.created_at)) < 5));
