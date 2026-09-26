CREATE TABLE party (
    id         BIGSERIAL PRIMARY KEY,
    map        VARCHAR(20)  NOT NULL,
    leader_id  BIGINT       NOT NULL REFERENCES member(id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_party_map CHECK (map IN ('GERAL', 'CAULDRON', 'ELAN'))
);

CREATE INDEX idx_party_map_created ON party (map, created_at);

CREATE TABLE party_member (
    party_id   BIGINT      NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    member_id  BIGINT      NOT NULL REFERENCES member(id),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (party_id, member_id),
    CONSTRAINT uq_party_member_member UNIQUE (member_id)
);

CREATE TABLE party_pending (
    id           BIGSERIAL PRIMARY KEY,
    party_id     BIGINT      NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    kind         VARCHAR(20) NOT NULL,
    initiator_id BIGINT      NOT NULL REFERENCES member(id),
    target_id    BIGINT      NOT NULL REFERENCES member(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_party_pending_kind CHECK (kind IN ('JOIN_REQUEST', 'INVITE')),
    CONSTRAINT chk_party_pending_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX idx_party_pending_join_request
    ON party_pending (party_id, initiator_id)
    WHERE status = 'PENDING' AND kind = 'JOIN_REQUEST';

CREATE UNIQUE INDEX idx_party_pending_invite
    ON party_pending (party_id, target_id)
    WHERE status = 'PENDING' AND kind = 'INVITE';

CREATE INDEX idx_party_pending_party_status ON party_pending (party_id, status);
CREATE INDEX idx_party_pending_target_status ON party_pending (target_id, status);

CREATE TABLE party_lfg (
    member_id  BIGINT PRIMARY KEY REFERENCES member(id),
    map        VARCHAR(20) NOT NULL,
    note       VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_party_lfg_map CHECK (map IN ('GERAL', 'CAULDRON', 'ELAN'))
);

CREATE INDEX idx_party_lfg_map ON party_lfg (map, created_at);
