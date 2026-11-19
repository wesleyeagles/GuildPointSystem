-- Pontos automáticos configuráveis (Admin → Pontos automáticos): cada regra é um "PB automático" que antes era fixo
-- no código (PbAutoService.RUNS). Shouts antes; RG = quem está no local no horário ganha N pontos; presença = quem
-- passou pelo local na janela ganha o objetivo. As rodadas continuam em pb_auto_run/presence/award pelo code.
CREATE TABLE auto_point_rule (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(32)  NOT NULL UNIQUE,
    label            VARCHAR(80)  NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    weekdays         SMALLINT     NOT NULL DEFAULT 127,       -- bit 0 = segunda … bit 6 = domingo (Brasília)
    shouts_before    VARCHAR(64)  NOT NULL DEFAULT '',        -- minutos antes do início, ex.: "12,7,2"
    shout            VARCHAR(200),
    shout_en         VARCHAR(200),
    rg_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    rg_at            TIME,
    rg_map           VARCHAR(64),
    rg_regions       TEXT         NOT NULL DEFAULT '',        -- uma por linha; vazio = o mapa inteiro
    rg_points        INT          NOT NULL DEFAULT 0,
    presence_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    presence_from    TIME,
    presence_to      TIME,
    presence_map     VARCHAR(64),
    presence_regions TEXT         NOT NULL DEFAULT '',
    objective_name   VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Os dois PBs que já eram automáticos, com os mesmos códigos (as rodadas já dadas continuam valendo).
INSERT INTO auto_point_rule (code, label, shouts_before, shout, shout_en,
                             rg_enabled, rg_at, rg_map, rg_regions, rg_points,
                             presence_enabled, presence_from, presence_to, presence_map, presence_regions, objective_name)
VALUES ('COLONIA_02', 'PB Colônia 02:00', '12,7,2', 'RG Base Avançada, Pontos de RG as 01:52',
        'RG at the Outpost, RG points at 11:52 (Jakarta time)',
        TRUE, '01:52', 'Cora HQ', 'Cora Outpost', 2,
        TRUE, '01:52', '02:05', 'Haram', E'Shadow Wood\nRock Cavern', 'PB Colônia'),
       ('HQ_22', 'PB HQ 22:00', '12,7,2', 'RG Base Avançada, Pontos de RG as 21:52',
        'RG at the Outpost, RG points at 07:52 (Jakarta time)',
        TRUE, '21:52', 'Cora HQ', 'Cora Outpost', 2,
        TRUE, '21:52', '22:05', 'Cora HQ', E'Dark Plain\nHunter Cave', 'PB HQ');
