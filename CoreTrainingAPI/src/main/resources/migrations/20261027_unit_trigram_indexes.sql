-- Migration: Add GIN Trigram index and type index on organization_units
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_units_name_trgm ON organization_units USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_units_type ON organization_units (type);
CREATE INDEX IF NOT EXISTS idx_units_active ON organization_units (active);
