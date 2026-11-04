-- Migration: Drop redundant and low-selectivity indexes to optimize write performance
-- 1. chapters & lessons: redundant with unique constraints (course_id, chapter_order) and (chapter_id, lesson_order)
DROP INDEX IF EXISTS idx_chapters_course_order;
DROP INDEX IF EXISTS idx_lessons_chapter_order;

-- 2. training_domains: low-cardinality status and small table trigram (search traverses course trigrams)
DROP INDEX IF EXISTS idx_domains_status;
DROP INDEX IF EXISTS idx_domains_name_trgm;
DROP INDEX IF EXISTS idx_domains_code_trgm;

-- 3. organization_units: small table (seq scan is faster and cleaner for small tables)
DROP INDEX IF EXISTS idx_units_type;
DROP INDEX IF EXISTS idx_units_active;
DROP INDEX IF EXISTS idx_units_name_trgm;
