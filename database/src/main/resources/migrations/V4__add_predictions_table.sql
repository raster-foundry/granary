CREATE TABLE predictions (
  id uuid primary key,
  model_id uuid references models(id) on delete cascade not null ,
  invoked_at timestamp with time zone not null,
  arguments jsonb not null,
  status job_status not null,
  status_reason text
);
