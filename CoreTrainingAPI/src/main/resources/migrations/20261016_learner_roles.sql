-- Optional manual migration for deployments without Hibernate ddl-auto=update.
BEGIN;
ALTER TABLE access_roles ADD COLUMN IF NOT EXISTS learner_role BOOLEAN;
UPDATE access_roles r
SET learner_role = COALESCE(r.learning_enabled, false)
    AND NOT COALESCE(r.protected_role, false)
    AND NOT COALESCE(r.assigned_courses_only, false)
    AND NOT EXISTS (SELECT 1 FROM access_role_permissions p WHERE p.role_id = r.id)
WHERE r.learner_role IS NULL;
DELETE FROM access_role_units WHERE role_id IN (SELECT id FROM access_roles WHERE learner_role = true);
DELETE FROM access_role_permissions WHERE role_id IN (SELECT id FROM access_roles WHERE learner_role = true);
UPDATE access_roles SET learning_enabled = true, assigned_courses_only = false, legacy_owner_scope = false
WHERE learner_role = true;
COMMIT;
