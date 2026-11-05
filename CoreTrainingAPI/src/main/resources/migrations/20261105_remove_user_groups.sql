-- Removes the learner-group feature. Hibernate ddl-auto=update never drops a table, and the foreign keys of these
-- tables to users, courses and organization_units would block deleting those rows, so every environment must run
-- this script after deploying a backend that no longer maps them. Safe to rerun.
-- Permanently deletes groups and memberships. Learners lose access to courses they received only through a group;
-- learning history is kept, and access returns if the course is granted by unit or the learner registers for it.
BEGIN;

DELETE FROM access_role_permissions WHERE permission LIKE 'groups:%';
DELETE FROM admin_page_actions WHERE page_id = 'groups';
DELETE FROM admin_pages WHERE id = 'groups';
DELETE FROM navigation_item_roles WHERE navigation_id = 'admin.groups';
DELETE FROM navigation_items WHERE id = 'admin.groups';

ALTER TABLE users DROP COLUMN IF EXISTS group_id;
DROP TABLE IF EXISTS group_mandatory_courses;
DROP TABLE IF EXISTS user_group_memberships;
DROP TABLE IF EXISTS user_groups;

COMMIT;
