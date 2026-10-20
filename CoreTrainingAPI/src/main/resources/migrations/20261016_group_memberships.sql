-- Optional manual migration for deployments without Hibernate ddl-auto=update.
-- The application also migrates legacy users.group_id on startup, once the join table exists.
BEGIN;
CREATE TABLE IF NOT EXISTS user_group_memberships (
    user_id BIGINT NOT NULL REFERENCES users(id),
    group_id VARCHAR(255) NOT NULL REFERENCES user_groups(id),
    PRIMARY KEY (user_id, group_id)
);
CREATE INDEX IF NOT EXISTS idx_group_memberships_group ON user_group_memberships(group_id);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'group_id') THEN
        INSERT INTO user_group_memberships(user_id, group_id)
        SELECT id, group_id FROM users WHERE group_id IS NOT NULL
        ON CONFLICT (user_id, group_id) DO NOTHING;
        UPDATE users SET group_id = NULL WHERE group_id IS NOT NULL;
    END IF;
END $$;
COMMIT;
