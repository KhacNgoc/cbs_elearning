-- Learner dashboard facts: chapters of the visible courses and lessons of each chapter. Without these
-- the chapter completion check scanned every chapter and lesson of the catalog.
CREATE INDEX IF NOT EXISTS idx_chapters_course ON chapters (course_id);
CREATE INDEX IF NOT EXISTS idx_lessons_chapter ON lessons (chapter_id);
