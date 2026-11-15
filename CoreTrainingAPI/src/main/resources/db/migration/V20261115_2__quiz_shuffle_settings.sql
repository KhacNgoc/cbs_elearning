-- Per-quiz settings: give each learner attempt its own question order and/or option order.
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS shuffle_options BOOLEAN NOT NULL DEFAULT FALSE;
