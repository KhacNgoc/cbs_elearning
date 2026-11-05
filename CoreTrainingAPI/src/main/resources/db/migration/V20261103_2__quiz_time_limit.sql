-- Optional per-quiz time limit; NULL means unlimited.
-- Hibernate ddl-auto=update adds this column on startup; this idempotent script
-- is for environments whose schema is managed manually.
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS time_limit_minutes integer;

-- One row per timed attempt; the server checks submissions against deadline_at.
CREATE TABLE IF NOT EXISTS quiz_sessions (
    id varchar(255) PRIMARY KEY,
    version bigint,
    user_id bigint NOT NULL REFERENCES users (id),
    lesson_id varchar(255) NOT NULL REFERENCES lessons (id),
    started_at timestamp(6) NOT NULL,
    deadline_at timestamp(6) NOT NULL,
    submitted_at timestamp(6)
);
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_user_lesson ON quiz_sessions (user_id, lesson_id);
