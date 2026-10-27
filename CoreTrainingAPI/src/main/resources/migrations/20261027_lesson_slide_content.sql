-- Apply before deployment when Hibernate ddl-auto=update is disabled.
-- Existing attachments remain supporting resources, including all PowerPoint files.
ALTER TABLE lesson_documents ADD COLUMN IF NOT EXISTS purpose varchar(20) NOT NULL DEFAULT 'SUPPORTING';
UPDATE lesson_documents SET purpose = 'SUPPORTING' WHERE purpose IS NULL;
ALTER TABLE lesson_documents ALTER COLUMN purpose SET DEFAULT 'SUPPORTING';
ALTER TABLE lesson_documents ALTER COLUMN purpose SET NOT NULL;

-- Do not delete progress, bookmarks, quiz scores or awarded achievements.
-- Content eligibility is recalculated from current text/video/SLIDE requirements by the application.
