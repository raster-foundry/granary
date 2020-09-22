ALTER TABLE executions ADD COLUMN tags text [] NOT NULL default '{}';

CREATE INDEX executions_tags_idx ON executions(tags);
