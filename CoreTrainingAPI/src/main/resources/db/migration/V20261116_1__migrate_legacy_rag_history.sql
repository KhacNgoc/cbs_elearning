-- Preserve the original per-user, per-course history in the conversation schema.
-- Hold the legacy tables until the copy and drop commit together.
LOCK TABLE rag_chat_messages, rag_chat_sources IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE legacy_rag_conversations ON COMMIT DROP AS
SELECT DISTINCT ON (user_id, course_id)
       user_id, course_id, gen_random_uuid()::text AS conversation_id,
       left(question, 120) AS title,
       min(created_at) OVER (PARTITION BY user_id, course_id) AS created_at,
       max(created_at) OVER (PARTITION BY user_id, course_id) AS updated_at
FROM rag_chat_messages
ORDER BY user_id, course_id, id;

INSERT INTO rag_conversations (id, user_id, course_id, title, summary, created_at, updated_at)
SELECT conversation_id, user_id, course_id, title, '', created_at, updated_at
FROM legacy_rag_conversations;

-- History pagination pairs each assistant with the preceding user message by ID.
-- Allocate IDs in legacy exchange order, with the user message first.
CREATE TEMP TABLE legacy_rag_messages ON COMMIT DROP AS
SELECT old.id AS legacy_id, conversation.conversation_id, roles.role,
       nextval(pg_get_serial_sequence('rag_conversation_messages', 'id')) AS message_id
FROM rag_chat_messages old
JOIN legacy_rag_conversations conversation USING (user_id, course_id)
CROSS JOIN (VALUES ('user', 1), ('assistant', 2)) AS roles(role, position)
ORDER BY old.id, roles.position;

INSERT INTO rag_conversation_messages (id, conversation_id, role, content, original_question,
                                      grounded, needs_clarification, created_at)
SELECT mapped.message_id, mapped.conversation_id, mapped.role,
       CASE WHEN mapped.role = 'user' THEN old.question ELSE old.answer END,
       old.question,
       CASE WHEN mapped.role = 'assistant' THEN old.grounded END,
       CASE WHEN mapped.role = 'assistant' THEN false END,
       old.created_at
FROM legacy_rag_messages mapped
JOIN rag_chat_messages old ON old.id = mapped.legacy_id;

INSERT INTO rag_conversation_sources (message_id, document_id, document_name, page, section, course_id, course_name)
SELECT mapped.message_id, source.document_id, source.document_name, source.page, source.section,
       course.id, course.name
FROM rag_chat_sources source
JOIN legacy_rag_messages mapped ON mapped.legacy_id = source.message_id AND mapped.role = 'assistant'
JOIN rag_chat_messages old ON old.id = source.message_id
JOIN courses course ON course.id = old.course_id
ORDER BY source.id;

DROP TABLE rag_chat_sources;
DROP TABLE rag_chat_messages;
