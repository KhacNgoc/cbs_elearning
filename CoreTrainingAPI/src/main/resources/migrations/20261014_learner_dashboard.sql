-- Optional manual migration for deployments without Hibernate ddl-auto=update.
ALTER TABLE courses ADD COLUMN IF NOT EXISTS business_module varchar(255);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS level varchar(255);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS keywords text;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS completion_deadline date;
ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS content_type varchar(255);
ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS document_id bigint;
ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS document_page integer;
ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS scroll_offset float8;
CREATE TABLE IF NOT EXISTS user_course_deadlines (
    user_id bigint NOT NULL REFERENCES users(id),
    course_id varchar(255) NOT NULL,
    due_date date,
    PRIMARY KEY(user_id, course_id)
);
