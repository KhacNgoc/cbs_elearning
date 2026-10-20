-- PostgreSQL: apply before starting the upgraded application when schema is managed manually.
-- Safe to rerun. Does not change existing users' roles, group assignments or course ownership.
BEGIN;
CREATE TABLE IF NOT EXISTS organization_units (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    type varchar(255) NOT NULL DEFAULT 'UNIT',
    parent_id varchar(255) REFERENCES organization_units(id),
    active boolean NOT NULL DEFAULT true
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS unit_id varchar(255) REFERENCES organization_units(id);
ALTER TABLE users ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version bigint NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS learner_type varchar(255) DEFAULT 'CURRENT_STAFF';
UPDATE users SET version=0 WHERE version IS NULL;
ALTER TABLE user_groups ADD COLUMN IF NOT EXISTS unit_id varchar(255) REFERENCES organization_units(id);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS owner_unit_id varchar(255) REFERENCES organization_units(id);
CREATE TABLE IF NOT EXISTS user_roles (
    user_id bigint NOT NULL REFERENCES users(id), role varchar(255) NOT NULL,
    PRIMARY KEY(user_id,role)
);
CREATE TABLE IF NOT EXISTS user_scope_units (
    user_id bigint NOT NULL REFERENCES users(id), unit_id varchar(255) NOT NULL REFERENCES organization_units(id),
    PRIMARY KEY(user_id,unit_id)
);
CREATE TABLE IF NOT EXISTS course_unit_grants (
    course_id varchar(255) NOT NULL REFERENCES courses(id), unit_id varchar(255) NOT NULL REFERENCES organization_units(id),
    grant_type varchar(255) NOT NULL, PRIMARY KEY(course_id,unit_id)
);
CREATE TABLE IF NOT EXISTS course_instructors (
    course_id varchar(255) NOT NULL REFERENCES courses(id), user_id bigint NOT NULL REFERENCES users(id),
    PRIMARY KEY(course_id,user_id)
);
CREATE TABLE IF NOT EXISTS uploaded_asset (
    path varchar(255) PRIMARY KEY, uploaded_by varchar(255) NOT NULL
);
ALTER TABLE password_reset_otps ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 0;
-- Old reset codes were plaintext; expire them during migration.
UPDATE password_reset_otps SET expiry_date=LEAST(expiry_date,CURRENT_TIMESTAMP);
CREATE INDEX IF NOT EXISTS idx_users_unit ON users(unit_id);
CREATE INDEX IF NOT EXISTS idx_courses_owner_unit ON courses(owner_unit_id);
CREATE INDEX IF NOT EXISTS idx_groups_unit ON user_groups(unit_id);
CREATE INDEX IF NOT EXISTS idx_course_grants_unit ON course_unit_grants(unit_id);
CREATE INDEX IF NOT EXISTS idx_instructor_user ON course_instructors(user_id);
COMMIT;
