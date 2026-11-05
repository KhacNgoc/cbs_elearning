package com.bidv.coretraining.repository.impl;

import com.bidv.coretraining.repository.NotificationNativeRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class NotificationNativeRepositoryImpl implements NotificationNativeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public NotificationNativeRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countCompletedLearners(String lessonId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM user_completed_lessons WHERE lesson_id = :lessonId",
                Map.of("lessonId", lessonId),
                Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public int insertForCompletedLearners(String lessonId, String title, String message, String targetUrl) {
        String insertSql = """
            INSERT INTO notifications (recipient_id, type, title, message, target_url, is_read, created_at)
            SELECT DISTINCT
                ucl.user_id,
                'COURSE_CONTENT_UPDATED',
                :title,
                :message,
                :targetUrl,
                false,
                NOW() AT TIME ZONE 'UTC'
            FROM user_completed_lessons ucl
            WHERE ucl.lesson_id = :lessonId
            """;
        return jdbc.update(insertSql, Map.of("lessonId", lessonId, "title", title, "message", message,
                "targetUrl", targetUrl != null ? targetUrl : ""));
    }

    @Override
    public List<String> completedLearnerUsernames(String lessonId) {
        return jdbc.queryForList(
                "SELECT DISTINCT u.username FROM users u JOIN user_completed_lessons ucl ON u.id = ucl.user_id WHERE ucl.lesson_id = :lessonId",
                Map.of("lessonId", lessonId), String.class);
    }
}
