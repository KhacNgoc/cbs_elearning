-- Per-content completion requirement. Every content type that already exists stays
-- required, so lessons keep behaving as before until an administrator changes them.
ALTER TABLE lesson_contents ADD COLUMN IF NOT EXISTS text_required boolean NOT NULL DEFAULT true;
ALTER TABLE lesson_contents ADD COLUMN IF NOT EXISTS video_required boolean NOT NULL DEFAULT true;
ALTER TABLE lesson_contents ADD COLUMN IF NOT EXISTS slide_required boolean NOT NULL DEFAULT true;
