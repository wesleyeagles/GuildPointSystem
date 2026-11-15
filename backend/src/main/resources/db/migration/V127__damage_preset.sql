-- Presets da calculadora de dano, por membro (antes só no navegador). state = JSON da página.
CREATE TABLE damage_preset (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    name       VARCHAR(40) NOT NULL,
    state      JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_damage_preset_member_name ON damage_preset (member_id, lower(name));
