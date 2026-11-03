package com.bidv.coretraining.entity;

import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.enums.FlashcardType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A lesson flashcard authored by an instructor or drafted by AI and approved by one.
 * Flashcards support learning only: they never count towards lesson completion.
 */
@Entity
@Table(name = "flashcards", indexes = {
        @Index(name = "idx_flashcards_lesson_order", columnList = "lesson_id,card_order"),
        @Index(name = "idx_flashcards_course_status", columnList = "course_id,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flashcard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FlashcardType type = FlashcardType.TERM;

    @Column(nullable = false, length = 1000)
    private String front;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String back;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FlashcardOrigin origin = FlashcardOrigin.MANUAL;

    /** DRAFT or PUBLISHED; only published cards reach learners. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    // Plain references: deleting a course document must not delete approved cards.
    @Column(name = "source_document_id")
    private String sourceDocumentId;

    @Column(name = "source_document_name", length = 500)
    private String sourceDocumentName;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "source_section", length = 2000)
    private String sourceSection;

    @Column(name = "card_order", nullable = false)
    @Builder.Default
    private Integer cardOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
