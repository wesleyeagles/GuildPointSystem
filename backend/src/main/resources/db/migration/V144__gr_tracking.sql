CREATE TABLE gr_tracker (
 member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
 added_by BIGINT REFERENCES member(id), added_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO gr_tracker(member_id)
 SELECT id FROM member WHERE lower(nickname) IN ('assassino','meow')
 AND status='APROVADO' AND profile_complete AND deleted_at IS NULL AND suspended_at IS NULL;
ALTER TABLE resource_spawn ADD COLUMN mob_code INT;
ALTER TABLE resource_spawn ADD COLUMN y INT;
ALTER TABLE resource_mob DROP CONSTRAINT resource_mob_tier_check;
ALTER TABLE resource_mob ADD CONSTRAINT resource_mob_tier_check CHECK(tier BETWEEN 0 AND 7);
INSERT INTO resource_mob(name,map_code,tier) VALUES
('[gr4](typeA) Mech. Warrior Exec',55,5),
('[gr4](typeB) Mech. Devastator',55,5),
('[gr4](typeC) Mech. Tower',55,5),
('[gr5](typeA) Mech. Chainsaw',55,6),
('[gr5](typeB) Mech. Tank',55,6),
('[gr5](typeC) Mech. Sword Defend',55,6),
('[gr6](typeA) Mech. Black Sign',55,7),
('[gr6](typeB) Mech. Bazooka',55,7),
('[gr6](typeC) Mech. Chip',55,7);
INSERT INTO resource_history_cursor(map_code) VALUES(55);
CREATE OR REPLACE FUNCTION resource_record_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE next_revision BIGINT;
BEGIN
    IF NEW.map_code NOT IN (2,10,11,55) THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' AND NEW.died_at IS NOT DISTINCT FROM OLD.died_at THEN RETURN NEW; END IF;
    UPDATE resource_history_cursor SET revision=revision+1 WHERE map_code=NEW.map_code
        RETURNING revision INTO next_revision;
    INSERT INTO resource_spawn_change(map_code,revision,spawn_id,kind)
        VALUES(NEW.map_code,next_revision,NEW.id,CASE WHEN TG_OP='INSERT' THEN 'FOUND' ELSE 'DIED' END);
    RETURN NEW;
END;
$$;
