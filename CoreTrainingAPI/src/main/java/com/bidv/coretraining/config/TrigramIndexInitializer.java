package com.bidv.coretraining.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class TrigramIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initTrigramIndexes() {
        try {
            String dbProduct = jdbcTemplate.execute((java.sql.Connection conn) -> conn.getMetaData().getDatabaseProductName());
            if (dbProduct != null && dbProduct.toLowerCase().contains("postgres")) {
                log.info("Configuring pg_trgm extension and GIN indexes for Course & Program search optimization...");
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");

                // Indexes for Courses
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_courses_name_lower_trgm ON courses USING gin (lower(name) gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_courses_code_lower_trgm ON courses USING gin (lower(code) gin_trgm_ops);");

                // Indexes for Training Programs
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_programs_name_trgm ON training_programs USING gin (name gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_programs_code_trgm ON training_programs USING gin (code gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_programs_name_lower_trgm ON training_programs USING gin (lower(name) gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_programs_code_lower_trgm ON training_programs USING gin (lower(code) gin_trgm_ops);");

                log.info("pg_trgm extension and GIN indexes verified successfully.");
            }
        } catch (Exception e) {
            log.warn("Could not initialize pg_trgm indexes (harmless if not supported or insufficient privileges): {}", e.getMessage());
        }
    }
}
