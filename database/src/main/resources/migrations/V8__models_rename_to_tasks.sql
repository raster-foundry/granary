ALTER TABLE models RENAME TO tasks;

ALTER TABLE predictions
  RENAME COLUMN model_id TO task_id;
