-- Optional manual migration for deployments without Hibernate ddl-auto=update.
ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS text_completed boolean NOT NULL DEFAULT false;
