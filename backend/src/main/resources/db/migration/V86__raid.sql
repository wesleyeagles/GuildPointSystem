-- Raid: grupo sem limite de integrantes, dividido em subgrupos de até 8 (uma PT do jogo cada).
CREATE TABLE raid (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    leader_id  BIGINT NOT NULL REFERENCES member(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Cada membro está em no máximo uma raid. group_leader marca quem chama o subgrupo no jogo;
-- sem marca, vale o líder da raid (se estiver no subgrupo) ou quem entrou primeiro.
CREATE TABLE raid_member (
    raid_id      BIGINT NOT NULL REFERENCES raid(id) ON DELETE CASCADE,
    member_id    BIGINT NOT NULL UNIQUE REFERENCES member(id) ON DELETE CASCADE,
    group_no     SMALLINT NOT NULL CHECK (group_no >= 1),
    group_leader BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (raid_id, member_id)
);
CREATE INDEX idx_raid_member_group ON raid_member(raid_id, group_no);
CREATE UNIQUE INDEX uq_raid_member_group_leader ON raid_member(raid_id, group_no) WHERE group_leader;

-- Convites pelo jogo, um por subgrupo: a DLL do líder do subgrupo digita "@party Nick"
-- (mesma busca da PT, /api/party-invites/pending).
CREATE TABLE raid_game_invite (
    raid_id      BIGINT NOT NULL REFERENCES raid(id) ON DELETE CASCADE,
    group_no     SMALLINT NOT NULL,
    id           UUID NOT NULL UNIQUE,
    leader_id    BIGINT NOT NULL REFERENCES member(id),
    actor_id     BIGINT NOT NULL REFERENCES member(id),
    nicks        JSONB NOT NULL,
    skipped      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (raid_id, group_no)
);
CREATE INDEX idx_raid_game_invite_leader ON raid_game_invite(leader_id);
