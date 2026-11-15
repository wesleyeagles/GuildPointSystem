-- Vídeo meme: Admin/Líder manda um vídeo do YouTube para um membro e a DLL dele abre o vídeo numa janela no
-- meio do jogo (DLL 1.45.0+). Quem não quiser receber marca a opção no próprio perfil.
ALTER TABLE member ADD COLUMN meme_video_opt_out BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE meme_video (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    sent_by      BIGINT      REFERENCES member(id) ON DELETE SET NULL,
    youtube_id   VARCHAR(11) NOT NULL CHECK (youtube_id ~ '^[A-Za-z0-9_-]{11}$'),
    start_second INT         NOT NULL DEFAULT 0 CHECK (start_second >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A DLL confirmou que abriu o vídeo.
    played_at    TIMESTAMPTZ
);
CREATE INDEX idx_meme_video_pending ON meme_video(member_id, created_at) WHERE played_at IS NULL;
