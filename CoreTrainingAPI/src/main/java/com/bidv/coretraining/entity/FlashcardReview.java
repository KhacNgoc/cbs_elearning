package com.bidv.coretraining.entity;

import com.bidv.coretraining.enums.FlashcardRating;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Spaced-repetition state of one learner for one flashcard. */
@Entity
@Table(name = "flashcard_reviews", uniqueConstraints = @UniqueConstraint(
        name = "uk_flashcard_reviews_user_card", columnNames = { "user_id", "flashcard_id" }), indexes = {
                @Index(name = "idx_flashcard_reviews_user_due", columnList = "user_id,due_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays;

    @Column(name = "ease_factor", nullable = false)
    private Double easeFactor;

    @Column(nullable = false)
    private Integer repetitions;

    @Column(nullable = false)
    private Integer lapses;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_rating", nullable = false, length = 20)
    private FlashcardRating lastRating;

    @Column(name = "last_reviewed_at", nullable = false)
    private LocalDateTime lastReviewedAt;
}
