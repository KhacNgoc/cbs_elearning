-- Migration: Add GIN Trigram indexes on training_domains for server-side search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_domains_name_trgm ON training_domains USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_domains_code_trgm ON training_domains USING gin (code gin_trgm_ops);
