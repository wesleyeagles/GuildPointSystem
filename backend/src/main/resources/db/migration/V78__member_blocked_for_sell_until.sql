-- "Blocked for sale" now has an end date: blocked while today (America/Sao_Paulo) <= this date.
ALTER TABLE member ADD COLUMN blocked_for_sell_until DATE;
ALTER TABLE member DROP COLUMN blocked_for_sell_account;
