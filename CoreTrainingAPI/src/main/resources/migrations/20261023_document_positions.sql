-- Optional manual migration for deployments without Hibernate ddl-auto=update.
ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS document_positions jsonb;
