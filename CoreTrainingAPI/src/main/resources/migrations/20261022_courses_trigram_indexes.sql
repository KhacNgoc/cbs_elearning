-- Migration: 20261022_courses_trigram_indexes.sql
-- Purpose: Optimize course searching by name and code using PostgreSQL pg_trgm and GIN Index

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram indexes for case-insensitive LIKE/ILIKE searches
CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_name_lower_trgm ON courses USING gin (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_lower_trgm ON courses USING gin (lower(code) gin_trgm_ops);
