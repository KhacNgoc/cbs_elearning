-- The question and option order the learner saw in the recorded result, as JSON
-- ([{"questionId":1,"optionOrder":[2,0,1,3]}, ...]). Null for results recorded before shuffling existed:
-- those are reviewed in the editor's order.
ALTER TABLE user_quiz_scores ADD COLUMN IF NOT EXISTS question_layout TEXT;
