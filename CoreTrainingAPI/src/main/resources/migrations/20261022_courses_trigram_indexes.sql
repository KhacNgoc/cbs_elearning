-- Migration: 20261022_courses_trigram_indexes.sql
-- Purpose: Optimize course and program searching by name and code using lean PostgreSQL pg_trgm and GIN Index

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Clean up redundant functional lower() indexes
DROP INDEX IF EXISTS idx_courses_name_lower_trgm;
DROP INDEX IF EXISTS idx_courses_code_lower_trgm;
DROP INDEX IF EXISTS idx_programs_name_lower_trgm;
DROP INDEX IF EXISTS idx_programs_code_lower_trgm;

-- 2 GIN trigram indexes for Courses
CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);

-- 2 GIN trigram indexes for Training Programs
CREATE INDEX IF NOT EXISTS idx_programs_name_trgm ON training_programs USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_programs_code_trgm ON training_programs USING gin (code gin_trgm_ops);
