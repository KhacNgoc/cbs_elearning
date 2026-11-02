-- Learning assistant searches every course the learner can access, so a
-- conversation is no longer bound to one course. Hibernate ddl-auto=update
-- adds the new columns but never relaxes NOT NULL, so apply this before deployment.
-- Existing conversations keep their original course_id.
ALTER TABLE rag_conversations ALTER COLUMN course_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rag_conversation_owner_updated ON rag_conversations (user_id, updated_at);

ALTER TABLE rag_conversation_sources ADD COLUMN IF NOT EXISTS course_id varchar(255);
ALTER TABLE rag_conversation_sources ADD COLUMN IF NOT EXISTS course_name varchar(500);
