-- Curriculum tree lookups: PostgreSQL does not index foreign keys, so every
-- "programs of a domain", "courses of a program" and per-lesson count used a full scan.

-- Programs of a page of domains, in tree order (domain_id, code).
CREATE INDEX IF NOT EXISTS idx_programs_domain_code ON training_programs (domain_id, code);

-- Courses of a program, paged in tree order, and course counts per program.
CREATE INDEX IF NOT EXISTS idx_courses_program_code ON courses (program_id, code);

-- Course-level quiz lookup for a page of courses.
CREATE INDEX IF NOT EXISTS idx_questions_course_lesson ON questions (course_id, lesson_id);

-- Per-lesson document and procedure counts in the chapter outline.
CREATE INDEX IF NOT EXISTS idx_lesson_documents_lesson ON lesson_documents (lesson_id);
CREATE INDEX IF NOT EXISTS idx_procedure_sections_lesson ON procedure_sections (lesson_id);

-- Domain search (ILIKE '%term%') like programs and courses already have.
CREATE INDEX IF NOT EXISTS idx_domains_name_trgm ON training_domains USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_domains_code_trgm ON training_domains USING gin (code gin_trgm_ops);
