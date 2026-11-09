-- Allocation now lives only on courses. Program and domain units mean "who manages it", and no longer give learners access.

-- 1. Removes units that batch allocation of a single course or program copied upward: no grant at that level, only below.
--    Units with a grant and units owning a course in the program are kept. Must run before the grants are read below.
DELETE FROM training_program_units pu
WHERE NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = pu.program_id AND g.unit_id = pu.unit_id)
  AND EXISTS (SELECT 1 FROM course_unit_grants g JOIN courses c ON c.id = g.course_id
              WHERE c.program_id = pu.program_id AND g.unit_id = pu.unit_id)
  AND NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = pu.program_id AND c.owner_unit_id = pu.unit_id);

DELETE FROM training_domain_units du
WHERE NOT EXISTS (SELECT 1 FROM domain_unit_grants g WHERE g.domain_id = du.domain_id AND g.unit_id = du.unit_id)
  AND (EXISTS (SELECT 1 FROM program_unit_grants g JOIN training_programs p ON p.id = g.program_id
               WHERE p.domain_id = du.domain_id AND g.unit_id = du.unit_id)
       OR EXISTS (SELECT 1 FROM course_unit_grants g JOIN courses c ON c.id = g.course_id
                  JOIN training_programs p ON p.id = c.program_id
                  WHERE p.domain_id = du.domain_id AND g.unit_id = du.unit_id));

-- 2. Copies program and domain grants onto their courses, so learners keep exactly what the old grants gave them.
--    An existing course grant wins, then the program grant, then the domain grant, as the old lookup order did.
INSERT INTO course_unit_grants (course_id, unit_id, grant_type)
SELECT c.id, g.unit_id, g.grant_type
FROM program_unit_grants g
JOIN courses c ON c.program_id = g.program_id
WHERE c.status IS DISTINCT FROM 'DELETED'
ON CONFLICT (course_id, unit_id) DO NOTHING;

INSERT INTO course_unit_grants (course_id, unit_id, grant_type)
SELECT c.id, g.unit_id, g.grant_type
FROM domain_unit_grants g
JOIN training_programs p ON p.domain_id = g.domain_id
JOIN courses c ON c.program_id = p.id
WHERE c.status IS DISTINCT FROM 'DELETED'
ON CONFLICT (course_id, unit_id) DO NOTHING;

-- program_unit_grants and domain_unit_grants are no longer read. They stay for one release so a rollback keeps its data.
