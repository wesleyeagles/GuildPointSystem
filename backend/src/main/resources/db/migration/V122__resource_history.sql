-- Per-map revisions are allocated under the map row lock before any resource write.
-- Unlike a sequence, this keeps cursor order consistent with transaction commit order.
LOCK TABLE resource_spawn IN SHARE ROW EXCLUSIVE MODE;
CREATE TABLE resource_history_cursor (
    map_code INT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
INSERT INTO resource_history_cursor(map_code) VALUES (2),(10),(11);

CREATE TABLE resource_spawn_change (
    map_code INT NOT NULL REFERENCES resource_history_cursor(map_code),
    revision BIGINT NOT NULL,
    spawn_id BIGINT NOT NULL REFERENCES resource_spawn(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('SNAPSHOT','FOUND','DIED')),
    PRIMARY KEY(map_code, revision)
);
CREATE INDEX idx_resource_spawn_change_spawn ON resource_spawn_change(spawn_id);

-- Includes historical deaths, not just the game's 90-second notification window.
INSERT INTO resource_spawn_change(map_code,revision,spawn_id,kind)
SELECT map_code,row_number() OVER (PARTITION BY map_code ORDER BY id),id,'SNAPSHOT'
FROM resource_spawn WHERE map_code IN (2,10,11);
UPDATE resource_history_cursor c SET revision=(
    SELECT coalesce(max(h.revision),0) FROM resource_spawn_change h WHERE h.map_code=c.map_code
);

-- Database-side capture also covers the old application during deployment/rollback.
CREATE FUNCTION resource_record_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE next_revision BIGINT;
BEGIN
    IF NEW.map_code NOT IN (2,10,11) THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' AND NEW.died_at IS NOT DISTINCT FROM OLD.died_at THEN RETURN NEW; END IF;
    UPDATE resource_history_cursor SET revision=revision+1 WHERE map_code=NEW.map_code
        RETURNING revision INTO next_revision;
    INSERT INTO resource_spawn_change(map_code,revision,spawn_id,kind)
        VALUES(NEW.map_code,next_revision,NEW.id,CASE WHEN TG_OP='INSERT' THEN 'FOUND' ELSE 'DIED' END);
    RETURN NEW;
END;
$$;
CREATE TRIGGER resource_history_change AFTER INSERT OR UPDATE OF died_at ON resource_spawn
    FOR EACH ROW EXECUTE FUNCTION resource_record_change();
