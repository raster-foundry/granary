CREATE TABLE predictions (
  id uuid primary key,
  model_id uuid references models(id) on delete cascade not null ,
  invoked_at timestamp with time zone not null,
  arguments jsonb not null,
  status job_status not null,
  status_reason text
);

CREATE INDEX predictions_status_idx ON predictions (status);
CREATE INDEX predictions_model_id_idx ON predictions (model_id);
