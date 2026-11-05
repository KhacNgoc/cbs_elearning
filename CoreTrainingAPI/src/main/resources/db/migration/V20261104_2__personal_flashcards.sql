-- Personal flashcards: a learner's own cards live in the flashcards table with an owner.
-- Lesson cards keep owner_user_id NULL. Hibernate ddl-auto=update adds the column on startup but
-- never widens the origin check it generated, so every environment must run this idempotent script.
ALTER TABLE flashcards ADD COLUMN IF NOT EXISTS owner_user_id bigint REFERENCES users (id);
CREATE INDEX IF NOT EXISTS idx_flashcards_owner_lesson ON flashcards (owner_user_id, lesson_id);
ALTER TABLE flashcards DROP CONSTRAINT IF EXISTS flashcards_origin_check;
ALTER TABLE flashcards ADD CONSTRAINT flashcards_origin_check CHECK (origin IN ('MANUAL', 'AI', 'PERSONAL'));
