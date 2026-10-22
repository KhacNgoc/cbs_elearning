-- Migration: 20261022_courses_trigram_indexes.sql
-- Purpose: Optimize course and program searching by name and code using PostgreSQL pg_trgm and GIN Index

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram indexes for Courses
CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_name_lower_trgm ON courses USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_lower_trgm ON courses USING gin (lower(code) gin_trgm_ops);

-- GIN trigram indexes for Training Programs
CREATE INDEX IF NOT EXISTS idx_programs_name_trgm ON training_programs USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_programs_code_trgm ON training_programs USING gin (code gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_programs_name_lower_trgm ON training_programs USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_programs_code_lower_trgm ON training_programs USING gin (lower(code) gin_trgm_ops);
