-- Reports read a learner's activity by user id; these two tables had no index that starts with it.
CREATE INDEX IF NOT EXISTS idx_user_quiz_scores_user ON user_quiz_scores (user_id);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_user ON quiz_attempts (user_id);
