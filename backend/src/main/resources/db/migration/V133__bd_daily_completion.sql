CREATE TABLE bd_daily_completion (
    character_key varchar(32) NOT NULL,
    quest_code varchar(7) NOT NULL CHECK (quest_code = 'Q070613'),
    day date NOT NULL,
    observed_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    previous_slot integer NOT NULL CHECK (previous_slot BETWEEN 0 AND 39),
    completed_slot integer NOT NULL CHECK (completed_slot = previous_slot + 103),
    definition_hash varchar(64) NOT NULL,
    PRIMARY KEY (character_key, quest_code, day)
);
CREATE INDEX bd_daily_completion_day ON bd_daily_completion(day);
