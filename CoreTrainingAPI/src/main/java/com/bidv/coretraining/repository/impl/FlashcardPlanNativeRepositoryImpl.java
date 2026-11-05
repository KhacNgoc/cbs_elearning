package com.bidv.coretraining.repository.impl;

import com.bidv.coretraining.repository.FlashcardPlanNativeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Map;

@Repository
public class FlashcardPlanNativeRepositoryImpl implements FlashcardPlanNativeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public FlashcardPlanNativeRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void updateNextReminders(Map<Long, Instant> nextReminders) {
        if (nextReminders.isEmpty())
            return;
        SqlParameterSource[] rows = nextReminders.entrySet().stream().map(row -> new MapSqlParameterSource()
                .addValue("id", row.getKey())
                .addValue("next", row.getValue() == null ? null : Timestamp.from(row.getValue()), Types.TIMESTAMP))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("UPDATE flashcard_study_plans SET next_reminder_at = :next WHERE id = :id", rows);
    }
}
