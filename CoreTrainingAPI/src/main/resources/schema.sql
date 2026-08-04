-- BIDV Core Training (WebCSR) - PostgreSQL Database Schema DDL
CREATE DATABASE IF NOT EXISTS coretraining

-- 1. Create topics table
CREATE TABLE IF NOT EXISTS topics (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    icon_name VARCHAR(255),
    quiz_passing_score INTEGER NOT NULL DEFAULT 80
);
ALTER TABLE topics ADD COLUMN IF NOT EXISTS quiz_passing_score INTEGER NOT NULL DEFAULT 80;

-- 2. Create user_groups table
CREATE TABLE IF NOT EXISTS user_groups (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

-- 3. Create group_mandatory_topics join table
CREATE TABLE IF NOT EXISTS group_mandatory_topics (
    group_id VARCHAR(255) NOT NULL,
    topic_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, topic_id),
    CONSTRAINT fk_group_mandatory_groups FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_mandatory_topics FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

-- 4. Create users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    avatar_color VARCHAR(255),
    role VARCHAR(255) NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    group_id VARCHAR(255),
    CONSTRAINT fk_users_group FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE SET NULL
);

-- 5. Create user_registered_topics join table
CREATE TABLE IF NOT EXISTS user_registered_topics (
    user_id BIGINT NOT NULL,
    topic_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, topic_id),
    CONSTRAINT fk_user_reg_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_reg_topics FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

-- 6. Create lessons table
CREATE TABLE IF NOT EXISTS lessons (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    hint TEXT,
    video_url VARCHAR(255),
    video_source VARCHAR(20),
    quiz_passing_score INTEGER NOT NULL DEFAULT 80,
    lesson_order INTEGER NOT NULL DEFAULT 0,
    topic_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_lessons_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

ALTER TABLE lessons ADD COLUMN IF NOT EXISTS video_source VARCHAR(20);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS quiz_passing_score INTEGER NOT NULL DEFAULT 80;
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS lesson_order INTEGER NOT NULL DEFAULT 0;
UPDATE lessons
SET video_source = CASE
    WHEN video_url LIKE '%/uploads/%' THEN 'UPLOAD'
    ELSE 'URL'
END
WHERE video_source IS NULL;

-- 7. Create lesson_documents table
CREATE TABLE IF NOT EXISTS lesson_documents (
    id BIGSERIAL PRIMARY KEY,
    lesson_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    file_url TEXT NOT NULL,
    original_file_name VARCHAR(255),
    file_size BIGINT,
    document_order INTEGER NOT NULL,
    CONSTRAINT fk_lesson_documents_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT chk_lesson_documents_type CHECK (document_type IN ('PDF', 'WORD', 'POWERPOINT'))
);

-- Keep existing databases in sync when POWERPOINT support is introduced.
ALTER TABLE lesson_documents DROP CONSTRAINT IF EXISTS chk_lesson_documents_type;
ALTER TABLE lesson_documents ADD CONSTRAINT chk_lesson_documents_type
    CHECK (document_type IN ('PDF', 'WORD', 'POWERPOINT'));

-- 8. Create user_completed_lessons join table
CREATE TABLE IF NOT EXISTS user_completed_lessons (
    user_id BIGINT NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT fk_user_completed_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_completed_lessons FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS lesson_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    last_position_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    watched_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    duration_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_lesson_progress_user_lesson UNIQUE (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

-- 9. Create procedure_sections table
CREATE TABLE IF NOT EXISTS procedure_sections (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    section_order INTEGER,
    CONSTRAINT fk_procedure_sections_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

-- 10. Create procedure_steps table (ElementCollection)
CREATE TABLE IF NOT EXISTS procedure_steps (
    section_id BIGINT NOT NULL,
    step_text TEXT,
    step_order INTEGER NOT NULL,
    PRIMARY KEY (section_id, step_order),
    CONSTRAINT fk_procedure_steps_section FOREIGN KEY (section_id) REFERENCES procedure_sections(id) ON DELETE CASCADE
);

-- 11. Create comments table
CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    avatar VARCHAR(255) NOT NULL,
    rating INTEGER,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    parent_comment_id BIGINT,
    reply_to_username VARCHAR(255),
    CONSTRAINT fk_comments_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES comments(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS topic_enrollment_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(255),
    rejection_reason TEXT,
    CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    target_url VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

CREATE TABLE IF NOT EXISTS navigation_items (
    id VARCHAR(255) PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    route VARCHAR(255) NOT NULL,
    icon VARCHAR(255) NOT NULL,
    area VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS navigation_item_roles (
    navigation_id VARCHAR(255) NOT NULL,
    role_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (navigation_id, role_name),
    CONSTRAINT fk_navigation_role_item FOREIGN KEY (navigation_id) REFERENCES navigation_items(id) ON DELETE CASCADE
);
ALTER TABLE comments ALTER COLUMN rating DROP NOT NULL;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS reply_to_username VARCHAR(255);

-- 12. Create private timestamped video notes
CREATE TABLE IF NOT EXISTS video_notes (
    id BIGSERIAL PRIMARY KEY,
    timestamp_seconds INTEGER NOT NULL CHECK (timestamp_seconds >= 0),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    lesson_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_video_notes_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT fk_video_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_video_notes_lesson_user_time
    ON video_notes (lesson_id, user_id, timestamp_seconds);

-- 13. Create questions table
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    correct_answer INTEGER NOT NULL,
    topic_id VARCHAR(255) NOT NULL,
    lesson_id VARCHAR(255),
    CONSTRAINT fk_questions_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_questions_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

ALTER TABLE questions ADD COLUMN IF NOT EXISTS lesson_id VARCHAR(255);

-- 14. Create question_options table (ElementCollection)
CREATE TABLE IF NOT EXISTS question_options (
    question_id BIGINT NOT NULL,
    option_text TEXT,
    option_order INTEGER NOT NULL,
    PRIMARY KEY (question_id, option_order),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 15. Create badges table
CREATE TABLE IF NOT EXISTS badges (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    icon VARCHAR(255) NOT NULL,
    topic_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_badges_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

-- 16. Create user_badges table
CREATE TABLE IF NOT EXISTS user_badges (
    id BIGSERIAL PRIMARY KEY,
    unlocked_at DATE NOT NULL,
    user_id BIGINT NOT NULL,
    badge_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_badges_badge FOREIGN KEY (badge_id) REFERENCES badges(id) ON DELETE CASCADE
);

-- 17. Create user_quiz_scores table
CREATE TABLE IF NOT EXISTS user_quiz_scores (
    id BIGSERIAL PRIMARY KEY,
    score INTEGER NOT NULL,
    total INTEGER NOT NULL,
    taken_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    topic_id VARCHAR(255) NOT NULL,
    lesson_id VARCHAR(255),
    CONSTRAINT fk_user_quiz_scores_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_quiz_scores_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_quiz_scores_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);
ALTER TABLE user_quiz_scores ADD COLUMN IF NOT EXISTS lesson_id VARCHAR(255);

CREATE TABLE IF NOT EXISTS user_quiz_score_answers (
    quiz_score_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_answer INTEGER NOT NULL,
    PRIMARY KEY (quiz_score_id, question_id),
    CONSTRAINT fk_quiz_score_answers_score FOREIGN KEY (quiz_score_id) REFERENCES user_quiz_scores(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_score_answers_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 17. Create password_reset_otps table
CREATE TABLE IF NOT EXISTS password_reset_otps (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    otp_code VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_reset_otp_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
