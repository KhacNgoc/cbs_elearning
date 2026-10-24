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
                log.info("Verifying lean GIN trigram indexes for Course & Program search optimization...");
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");

                // Clean up redundant lower() indexes if they exist
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_courses_name_lower_trgm;");
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_courses_code_lower_trgm;");
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_programs_name_lower_trgm;");
                jdbcTemplate.execute("DROP INDEX IF EXISTS idx_programs_code_lower_trgm;");

                // 2 Clean GIN trigram indexes for Courses
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);");

                // 2 Clean GIN trigram indexes for Training Programs
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_programs_name_trgm ON training_programs USING gin (name gin_trgm_ops);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_programs_code_trgm ON training_programs USING gin (code gin_trgm_ops);");

                log.info("Lean GIN trigram indexes verified successfully (2 per table).");
            }
        } catch (Exception e) {
            log.warn("Could not initialize pg_trgm indexes: {}", e.getMessage());
        }
    }
}
