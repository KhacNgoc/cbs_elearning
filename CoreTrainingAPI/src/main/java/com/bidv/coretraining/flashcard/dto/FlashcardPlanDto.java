package com.bidv.coretraining.flashcard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Data contracts for learners' flashcard study plans. */
public final class FlashcardPlanDto {
    private FlashcardPlanDto() {
    }

    /** cardIds null means the whole lesson, including cards added to it later. */
    public record Item(@NotBlank @Size(max = 255) String lessonId, @Size(max = 500) List<Long> cardIds) {
    }

    public record Plan(Long id, String name, LocalDate startDate, LocalDate endDate, List<Integer> weekdays,
            @JsonFormat(pattern = "HH:mm") LocalTime reminderTime, boolean active,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant nextReminderAt, List<Item> items, Long version) {
    }

    /** items null on an update keeps the plan's cards. */
    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            List<@Min(1) @Max(7) Integer> weekdays,
            @JsonFormat(pattern = "HH:mm") LocalTime reminderTime,
            Boolean active,
            @Size(max = 500) List<@Valid Item> items,
            Long version) {
    }

    public record Session(Long planId, String name, List<FlashcardDto.Card> cards) {
    }
}
