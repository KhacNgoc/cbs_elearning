-- A passed required lesson quiz now completes the lesson on its own. Award the lessons that
-- learners already passed under the old rule, which also waited for every required content.
-- Mirrors LessonProgressServiceImpl.recordCompletion: the completion, +100 points, and the
-- course completion once every learnable lesson of every learnable chapter is done.
CREATE TEMP TABLE required_quiz_backfill ON COMMIT DROP AS
SELECT DISTINCT s.user_id, l.id AS lesson_id, l.course_id
FROM user_quiz_scores s
JOIN lessons l ON l.id = s.lesson_id
JOIN quizzes q ON q.lesson_id = l.id
WHERE q.required_for_progress
  AND EXISTS (SELECT 1 FROM questions qs WHERE qs.quiz_id = q.id)
  AND s.total > 0
  AND round(s.score * 100.0 / s.total) >= q.passing_score
  AND NOT EXISTS (SELECT 1 FROM user_completed_lessons d
                  WHERE d.user_id = s.user_id AND d.lesson_id = l.id);

INSERT INTO user_completed_lessons (user_id, lesson_id)
SELECT user_id, lesson_id FROM required_quiz_backfill;

UPDATE users u SET points = u.points + 100 * b.awarded
FROM (SELECT user_id, count(*) AS awarded FROM required_quiz_backfill GROUP BY user_id) b
WHERE u.id = b.user_id;

INSERT INTO user_completed_courses (user_id, course_id, completed_at)
SELECT b.user_id, b.course_id, (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')
FROM (SELECT DISTINCT user_id, course_id FROM required_quiz_backfill) b
WHERE NOT EXISTS (SELECT 1 FROM user_completed_courses c
                  WHERE c.user_id = b.user_id AND c.course_id = b.course_id)
  AND EXISTS (SELECT 1 FROM chapters ch WHERE ch.course_id = b.course_id
              AND (ch.status IS NULL OR upper(ch.status) IN ('PUBLISHED', 'ACTIVE')))
  -- every learnable chapter has a learnable lesson, and every such lesson is completed
  AND NOT EXISTS (
    SELECT 1 FROM chapters ch WHERE ch.course_id = b.course_id
      AND (ch.status IS NULL OR upper(ch.status) IN ('PUBLISHED', 'ACTIVE'))
      AND (NOT EXISTS (SELECT 1 FROM lessons l WHERE l.chapter_id = ch.id
                       AND (l.status IS NULL OR upper(l.status) IN ('PUBLISHED', 'ACTIVE')))
           OR EXISTS (SELECT 1 FROM lessons l WHERE l.chapter_id = ch.id
                      AND (l.status IS NULL OR upper(l.status) IN ('PUBLISHED', 'ACTIVE'))
                      AND NOT EXISTS (SELECT 1 FROM user_completed_lessons d
                                      WHERE d.user_id = b.user_id AND d.lesson_id = l.id))));
