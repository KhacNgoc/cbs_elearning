package com.bidv.coretraining.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps persisted upload references independent from the API host and port.
 */
@Component
public class UploadedPathMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public UploadedPathMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        normalizeColumn("lessons", "video_url");
        normalizeColumn("lesson_documents", "file_url");
        jdbcTemplate.update("""
                UPDATE lessons
                SET video_source = CASE
                    WHEN video_url LIKE '%/uploads/%' THEN 'UPLOAD'
                    ELSE 'URL'
                END
                WHERE video_source IS NULL
                """);
    }

    private void normalizeColumn(String table, String column) {
        jdbcTemplate.queryForList(
                "SELECT DISTINCT " + column + " FROM " + table
                        + " WHERE " + column + " IS NOT NULL",
                String.class
        ).forEach(value -> {
            int uploadsIndex = value.indexOf("/uploads/");
            if (uploadsIndex > 0) {
                jdbcTemplate.update(
                        "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?",
                        value.substring(uploadsIndex),
                        value
                );
            }
        });
    }
}
