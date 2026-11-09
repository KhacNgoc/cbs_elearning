-- A learner can have only one pending request per course. Earlier concurrent submits could create several,
-- so the oldest request of each group is kept and the newer duplicates are removed. Reviewed requests are not touched.
DELETE FROM course_enrollment_requests newer
USING course_enrollment_requests older
WHERE newer.status = 'PENDING'
  AND older.status = 'PENDING'
  AND newer.user_id = older.user_id
  AND newer.course_id = older.course_id
  AND newer.id > older.id;

CREATE UNIQUE INDEX uq_enrollment_pending_user_course
    ON course_enrollment_requests (user_id, course_id)
    WHERE status = 'PENDING';
