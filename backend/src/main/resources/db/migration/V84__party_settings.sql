-- Party board settings (single row). lfg_locked: Administrador/Líder closed the waiting list, so
-- members cannot join it on their own; whoever is already waiting stays.
CREATE TABLE party_settings (
    id            SMALLINT    PRIMARY KEY DEFAULT 1,
    lfg_locked    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by_id BIGINT      REFERENCES member(id),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT chk_party_settings_single CHECK (id = 1)
);

INSERT INTO party_settings (id) VALUES (1);
