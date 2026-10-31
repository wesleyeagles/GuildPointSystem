-- Extend the existing queue control; keep existing closures until the next 06h/18h boundary.
ALTER TABLE party_settings ADD COLUMN lfg_override_until TIMESTAMPTZ;

UPDATE party_settings
SET lfg_override_until = (
    date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo') +
    CASE
        WHEN extract(hour FROM now() AT TIME ZONE 'America/Sao_Paulo') < 6 THEN interval '6 hours'
        WHEN extract(hour FROM now() AT TIME ZONE 'America/Sao_Paulo') < 18 THEN interval '18 hours'
        ELSE interval '1 day 6 hours'
    END
) AT TIME ZONE 'America/Sao_Paulo'
WHERE lfg_locked;
