CREATE TABLE models (
  id uuid primary key,
  name text not null,
  validator jsonb not null,
  job_definition text not null,
  job_queue text not null
);
