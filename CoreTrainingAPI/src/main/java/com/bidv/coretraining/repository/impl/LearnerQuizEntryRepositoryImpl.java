package com.bidv.coretraining.repository.impl;

import com.bidv.coretraining.repository.LearnerQuizEntryRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.Optional;

@Repository
public class LearnerQuizEntryRepositoryImpl implements LearnerQuizEntryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public LearnerQuizEntryRepositoryImpl(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Score> latestScore(Long userId, String courseId, String lessonId) {
        var params = new HashMap<String, Object>();
        params.put("uid", userId); params.put("cid", courseId); params.put("lid", lessonId);
        var rows = jdbc.query("select score,total from user_quiz_scores where user_id=:uid and course_id=:cid and "
                + (lessonId == null ? "lesson_id is null" : "lesson_id=:lid")
                + " order by taken_at desc,id desc limit 1", params,
                (row, index) -> new Score((Integer) row.getObject("score"), (Integer) row.getObject("total")));
        return rows.stream().findFirst();
    }
}
