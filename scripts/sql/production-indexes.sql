-- PostgreSQL 15+: consolidated inventory of the 16 explicit application indexes.
-- Sources: CoreTrainingAPI entity @Index, resources/schema.sql and migrations/.
-- Apply table/column migrations FIRST. This script does not create tables,
-- migrate data, or recreate indexes supplied by PRIMARY KEY / UNIQUE constraints.
-- Schema: public. Run only one copy of this script at a time.
--
-- Run with psql (replace connection arguments; do not use --single-transaction):
-- psql -X -h HOST -p 5432 -U USER -d DATABASE -v ON_ERROR_STOP=1 -f scripts/sql/production-indexes.sql
-- GUI clients: enable autocommit, stop on error, use the SAME connection for
-- every statement, and do not wrap the script in BEGIN/COMMIT.
-- CREATE INDEX CONCURRENTLY must execute outside a transaction block.
--
-- Existing valid indexes with the expected name/columns are retained. An index
-- under another name is not automatically removed or renamed; review equivalent
-- indexes in that situation to avoid duplicate storage/write overhead.
-- IF NOT EXISTS does NOT repair invalid indexes or update changed definitions.
-- Preflight below rejects those cases instead of silently reporting success.
-- If a concurrent build fails, inspect pg_index.indisvalid; after investigation,
-- DROP INDEX CONCURRENTLY public.<failed_index> outside a transaction and rerun.
-- Do not drop a valid index without reviewing its use and dependencies.

SET lock_timeout = '5s';
SET statement_timeout = '0';

-- Session-only inventory; no persistent helper objects are installed.
CREATE TEMP TABLE IF NOT EXISTS cbs_production_index_inventory (
    index_name text PRIMARY KEY,
    table_name text NOT NULL,
    column_names text[] NOT NULL
);
TRUNCATE pg_temp.cbs_production_index_inventory;
INSERT INTO pg_temp.cbs_production_index_inventory VALUES
    ('idx_users_unit', 'users', ARRAY['unit_id']),
    ('idx_courses_owner_unit', 'courses', ARRAY['owner_unit_id']),
    ('idx_groups_unit', 'user_groups', ARRAY['unit_id']),
    ('idx_course_grants_unit', 'course_unit_grants', ARRAY['unit_id']),
    ('idx_instructor_user', 'course_instructors', ARRAY['user_id']),
    ('idx_group_memberships_group', 'user_group_memberships', ARRAY['group_id']),
    ('idx_bank_questions_course', 'bank_questions', ARRAY['course_id']),
    ('idx_lesson_reports_lesson', 'lesson_reports', ARRAY['lesson_id']),
    ('idx_lesson_reports_created', 'lesson_reports', ARRAY['created_at']),
    ('ix_course_ratings_course_updated', 'course_ratings', ARRAY['course_id', 'updated_at']),
    ('idx_notifications_recipient_created', 'notifications', ARRAY['recipient_id', 'created_at']),
    ('idx_video_notes_lesson_user_time', 'video_notes', ARRAY['lesson_id', 'user_id', 'timestamp_seconds']),
    ('idx_rag_conversation_owner_course_updated', 'rag_conversations', ARRAY['user_id', 'course_id', 'updated_at']),
    ('idx_rag_conversation_message_order', 'rag_conversation_messages', ARRAY['conversation_id', 'id']),
    ('idx_lesson_knowledge_source_lesson', 'lesson_knowledge_sources', ARRAY['lesson_id']),
    ('idx_lesson_knowledge_source_document', 'lesson_knowledge_sources', ARRAY['document_id']);

-- Fail before building any indexes when prerequisites are absent or an existing
-- namesake is invalid / refers to a different table or set/order of key columns.
DO $preflight$
DECLARE
    expected record;
    relation_id oid;
    existing_id oid;
    key_column text;
    actual record;
BEGIN
    FOR expected IN SELECT * FROM pg_temp.cbs_production_index_inventory LOOP
        relation_id := to_regclass(format('public.%I', expected.table_name));
        IF relation_id IS NULL THEN
            RAISE EXCEPTION 'Missing table public.%. Apply schema migrations first.', expected.table_name;
        END IF;
        IF (SELECT relkind FROM pg_class WHERE oid = relation_id) <> 'r' THEN
            RAISE EXCEPTION 'public.% is not an ordinary table; review concurrent indexing for partitions.', expected.table_name;
        END IF;
        FOREACH key_column IN ARRAY expected.column_names LOOP
            IF NOT EXISTS (SELECT 1 FROM pg_attribute
                           WHERE attrelid = relation_id AND attname = key_column
                             AND attnum > 0 AND NOT attisdropped) THEN
                RAISE EXCEPTION 'Missing column public.%.%. Apply schema migrations first.', expected.table_name, key_column;
            END IF;
        END LOOP;
        existing_id := to_regclass(format('public.%I', expected.index_name));
        IF existing_id IS NOT NULL THEN
            SELECT i.indrelid, i.indisvalid, i.indisready, i.indisunique,
                   i.indpred IS NULL AS no_predicate, i.indexprs IS NULL AS no_expression,
                   i.indnatts = i.indnkeyatts AS no_include, am.amname,
                   ARRAY(SELECT a.attname::text
                         FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, position)
                         LEFT JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
                         ORDER BY k.position) AS columns
              INTO actual
              FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
              JOIN pg_am am ON am.oid = c.relam
             WHERE i.indexrelid = existing_id;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'public.% exists but is not an index.', expected.index_name;
            END IF;
            IF NOT actual.indisvalid OR NOT actual.indisready THEN
                RAISE EXCEPTION 'Index public.% is INVALID/not ready. Inspect and repair it before rerunning.', expected.index_name;
            END IF;
            IF actual.indrelid <> relation_id OR actual.columns IS DISTINCT FROM expected.column_names
               OR actual.amname <> 'btree' OR actual.indisunique
               OR NOT actual.no_predicate OR NOT actual.no_expression OR NOT actual.no_include THEN
                RAISE EXCEPTION 'Index public.% differs from expected btree on public.%(%) -- review manually.',
                    expected.index_name, expected.table_name, array_to_string(expected.column_names, ', ');
            END IF;
        END IF;
    END LOOP;
END
$preflight$;

-- Organization/access: migrations/20261012_access_control.sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_unit ON public.users (unit_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_courses_owner_unit ON public.courses (owner_unit_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_groups_unit ON public.user_groups (unit_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_course_grants_unit ON public.course_unit_grants (unit_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instructor_user ON public.course_instructors (user_id);

-- Group membership: migrations/20261016_group_memberships.sql, entity User
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_group_memberships_group ON public.user_group_memberships (group_id);

-- Question bank/reports: migrations/20261014_content_reports_question_bank.sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_bank_questions_course ON public.bank_questions (course_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lesson_reports_lesson ON public.lesson_reports (lesson_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lesson_reports_created ON public.lesson_reports (created_at);

-- Ratings: the migration uses updated_at DESC; Hibernate's entity uses ASC.
-- Keep either existing direction (preflight checks column order, not ASC/DESC).
-- Both support course_id equality + updated_at ordering via forward/backward scan.
-- Missing index is created with DESC, matching the explicit migration.
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_course_ratings_course_updated ON public.course_ratings (course_id, updated_at DESC);

-- Notifications, notes, RAG: schema.sql and corresponding entities
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_recipient_created ON public.notifications (recipient_id, created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_video_notes_lesson_user_time ON public.video_notes (lesson_id, user_id, timestamp_seconds);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rag_conversation_owner_course_updated ON public.rag_conversations (user_id, course_id, updated_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rag_conversation_message_order ON public.rag_conversation_messages (conversation_id, id);

-- LessonKnowledgeSource entity (these two are absent from the checked-in dump)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lesson_knowledge_source_lesson ON public.lesson_knowledge_sources (lesson_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lesson_knowledge_source_document ON public.lesson_knowledge_sources (document_id);

-- Deployment report: expect 16 rows, all status=OK. Preserve indexdef in the
-- deployment log; a future definition change needs a new migration, not just IF NOT EXISTS.
SELECT e.table_name, e.index_name,
       CASE WHEN i.indisvalid AND i.indisready THEN 'OK' ELSE 'MISSING / INVALID' END AS status,
       pg_get_indexdef(c.oid) AS index_definition
FROM pg_temp.cbs_production_index_inventory e
LEFT JOIN pg_namespace n ON n.nspname = 'public'
LEFT JOIN pg_class c ON c.relnamespace = n.oid AND c.relname = e.index_name
LEFT JOIN pg_index i ON i.indexrelid = c.oid
ORDER BY e.table_name, e.index_name;

DROP TABLE pg_temp.cbs_production_index_inventory;
RESET lock_timeout;
RESET statement_timeout;
