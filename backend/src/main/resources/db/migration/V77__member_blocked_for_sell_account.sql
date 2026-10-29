-- Líder/Administrador flag: this member's game account is blocked for sale.
ALTER TABLE member ADD COLUMN blocked_for_sell_account BOOLEAN NOT NULL DEFAULT FALSE;
