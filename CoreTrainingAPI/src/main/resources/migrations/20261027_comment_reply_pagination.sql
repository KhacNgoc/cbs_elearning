-- Optional manual index for deployments without Hibernate schema updates.
CREATE INDEX IF NOT EXISTS idx_comment_replies_page ON comments (parent_comment_id, created_at, id);
