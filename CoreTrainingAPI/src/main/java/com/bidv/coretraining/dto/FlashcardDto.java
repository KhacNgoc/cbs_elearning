package com.bidv.coretraining.dto;

import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.enums.FlashcardRating;
import com.bidv.coretraining.enums.FlashcardType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Data contracts for lesson flashcards and their spaced-repetition reviews. */
public final class FlashcardDto {
    private FlashcardDto() {
    }

    public record Source(String documentId, String documentName, Integer page, String section) {
    }

    /**
     * The learner's schedule for a card; null on cards the learner has never reviewed.
     * dueAt is an instant so browsers in another time zone than the server compare it correctly.
     */
    public record Progress(Instant dueAt, int intervalDays, int repetitions, int lapses,
            FlashcardRating lastRating, boolean due, boolean mastered) {
    }

    public record Card(Long id, String courseId, String lessonId, String lessonTitle, FlashcardType type,
            String front, String back, Source source, Progress progress) {
    }

    public record Stats(int total, int newCount, int dueCount, int learningCount, int masteredCount) {
    }

    public record Deck(String lessonId, List<Card> cards, Stats stats) {
    }

    public record ReviewRequest(@NotNull FlashcardRating rating) {
    }

    public record CourseDue(String courseId, String courseName, int due) {
    }

    public record Summary(int dueTotal, List<CourseDue> courses) {
    }

    public record ReviewQueue(String courseId, String courseName, int dueTotal, List<Card> cards) {
    }

    public record AdminCard(Long id, String lessonId, FlashcardType type, String front, String back,
            FlashcardOrigin origin, String status, Source source, Integer cardOrder, LocalDateTime updatedAt,
            Long version) {
    }

    public record CardRequest(
            @NotNull FlashcardType type,
            @NotBlank @Size(max = 1000) String front,
            @NotBlank @Size(max = 4000) String back,
            @Pattern(regexp = "DRAFT|PUBLISHED") String status,
            FlashcardOrigin origin,
            @Size(max = 255) String sourceDocumentId,
            @Min(1) Integer sourcePage,
            @Size(max = 2000) String sourceSection,
            Long version) {
    }

    public record BatchRequest(@NotEmpty @Size(max = 50) List<@Valid CardRequest> cards) {
    }

    public record StatusRequest(@NotEmpty @Size(max = 500) List<Long> ids,
            @NotNull @Pattern(regexp = "DRAFT|PUBLISHED") String status) {
    }

    public record GenerationRequest(@Min(1) @Max(20) int count, @Size(max = 2) List<FlashcardType> types) {
    }
}
