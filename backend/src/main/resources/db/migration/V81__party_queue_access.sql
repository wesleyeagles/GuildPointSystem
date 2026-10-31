CREATE TABLE party_queue_access (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    override_open BOOLEAN,
    override_until TIMESTAMPTZ,
    CHECK ((override_open IS NULL) = (override_until IS NULL))
);
INSERT INTO party_queue_access(id) VALUES (1);
