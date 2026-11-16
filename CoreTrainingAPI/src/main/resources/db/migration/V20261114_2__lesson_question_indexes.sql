-- Found by the large-catalog load test (~720k lessons, ~720k questions): these foreign keys had no
-- index, so every "lessons of a course" or "questions of a quiz/lesson" read scanned the whole table
-- (chapter outline, learning pages, progress, course deletion).

-- Lessons of a course: chapter outline, learner outline, progress and course-level operations.
CREATE INDEX IF NOT EXISTS idx_lessons_course ON lessons (course_id);

-- Questions of a quiz (outline quiz flag, quiz loading) and of a lesson.
CREATE INDEX IF NOT EXISTS idx_questions_quiz ON questions (quiz_id);
CREATE INDEX IF NOT EXISTS idx_questions_lesson ON questions (lesson_id);
