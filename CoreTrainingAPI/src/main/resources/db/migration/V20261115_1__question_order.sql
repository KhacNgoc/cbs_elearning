-- A quiz keeps its questions in an explicit order. Before this column the order came from the row
-- order the database happened to return, which changed whenever a question was updated (for example
-- after saving it to the question bank).
ALTER TABLE questions ADD COLUMN IF NOT EXISTS question_order INTEGER;

-- Existing questions keep their creation order within each quiz.
UPDATE questions q
SET question_order = ordered.position
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY quiz_id ORDER BY id) - 1 AS position
    FROM questions
) ordered
WHERE q.id = ordered.id AND q.question_order IS NULL;

ALTER TABLE questions ALTER COLUMN question_order SET NOT NULL;

