TRUNCATE TABLE tokens;

ALTER TABLE tokens
  ADD COLUMN email text NOT NULL,
  ADD COLUMN user_id uuid NOT NULL UNIQUE;

ALTER TABLE tasks
  ADD COLUMN owner uuid references tokens(user_id);

ALTER TABLE executions
  ADD COLUMN owner uuid references tokens(user_id);
