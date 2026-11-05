package com.bidv.coretraining.service;

import com.bidv.coretraining.policy.FlashcardPlanSchedule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlashcardPlanScheduleTest {
    private static final LocalDate MONDAY = LocalDate.parse("2026-11-02");
    private static final LocalTime EVENING = LocalTime.of(20, 0);

    private static Instant next(LocalDate start, LocalDate end, int mask, String after) {
        return FlashcardPlanSchedule.nextReminder(start, end, mask, EVENING, Instant.parse(after));
    }

    @Test void remindersFollowVietnamTimeWhateverTheServerZone() {
        // 20:00 in Vietnam is 13:00 UTC.
        assertThat(next(MONDAY, MONDAY, FlashcardPlanSchedule.EVERY_DAY, "2026-11-02T00:00:00Z"))
                .isEqualTo("2026-11-02T13:00:00Z");
    }

    @Test void aPassedTimeMovesToTheNextChosenWeekday() {
        int mondayAndFriday = FlashcardPlanSchedule.mask(List.of(1, 5));
        assertThat(next(MONDAY, MONDAY.plusDays(30), mondayAndFriday, "2026-11-02T13:00:00Z"))
                .isEqualTo("2026-11-06T13:00:00Z");
    }

    @Test void theVietnamDateDecidesTheWeekdayNotTheUtcOne() {
        // Sunday 18:00 UTC is already Monday 01:00 in Vietnam.
        int monday = FlashcardPlanSchedule.mask(List.of(1));
        assertThat(next(MONDAY.minusDays(7), MONDAY.plusDays(7), monday, "2026-11-01T18:00:00Z"))
                .isEqualTo("2026-11-02T13:00:00Z");
    }

    @Test void nothingRemainsAfterTheEndOrWithoutATime() {
        assertThat(next(MONDAY, MONDAY, FlashcardPlanSchedule.EVERY_DAY, "2026-11-02T13:00:00Z")).isNull();
        assertThat(FlashcardPlanSchedule.nextReminder(MONDAY, MONDAY, FlashcardPlanSchedule.EVERY_DAY, null,
                Instant.parse("2026-11-01T00:00:00Z"))).isNull();
    }

    @Test void aFutureStartWaitsForItsFirstDay() {
        assertThat(next(MONDAY.plusDays(4), MONDAY.plusDays(30), FlashcardPlanSchedule.EVERY_DAY, "2026-11-02T00:00:00Z"))
                .isEqualTo("2026-11-06T13:00:00Z");
    }

    @Test void weekdaysRoundTrip() {
        assertThat(FlashcardPlanSchedule.weekdays(FlashcardPlanSchedule.mask(List.of(7, 1, 3)))).containsExactly(1, 3, 7);
        assertThat(FlashcardPlanSchedule.weekdays(FlashcardPlanSchedule.EVERY_DAY)).hasSize(7);
    }
}
