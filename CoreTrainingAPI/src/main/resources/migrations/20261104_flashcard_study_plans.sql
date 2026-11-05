-- Flashcard study plans. Hibernate ddl-auto=update creates the table but never widens the notification type check
-- it generated, so every environment must run this idempotent script or reminders fail to save.
BEGIN;
CREATE TABLE IF NOT EXISTS flashcard_study_plans (
    id bigserial PRIMARY KEY,
    version bigint,
    user_id bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    weekday_mask integer NOT NULL,
    reminder_time time(6),
    active boolean NOT NULL,
    next_reminder_at timestamp(6) with time zone,
    selection text NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_flashcard_plans_user ON flashcard_study_plans (user_id);
CREATE INDEX IF NOT EXISTS idx_flashcard_plans_next_reminder ON flashcard_study_plans (next_reminder_at);
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'COMMENT_REPLY', 'ENROLLMENT_REQUEST', 'ENROLLMENT_APPROVED', 'ENROLLMENT_REJECTED', 'LESSON_REPORT',
    'COURSE_CONTENT_UPDATED', 'FLASHCARD_STUDY_REMINDER'));
COMMIT;
