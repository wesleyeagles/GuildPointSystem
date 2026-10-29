-- Líder/Administrador: last day (inclusive, America/Sao_Paulo) the member's game account is blocked for sale.
-- The block lifts by itself after that day; null = not blocked.
ALTER TABLE member ADD COLUMN blocked_for_sell_until DATE;
