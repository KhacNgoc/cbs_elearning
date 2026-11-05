package com.bidv.coretraining.flashcard.entity;

import com.bidv.coretraining.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.OptimisticLock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A learner's own schedule for studying chosen flashcards, and when it next reminds them. */
@Entity
@Table(name = "flashcard_study_plans", indexes = {
        @Index(name = "idx_flashcard_plans_user", columnList = "user_id"),
        @Index(name = "idx_flashcard_plans_next_reminder", columnList = "next_reminder_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardStudyPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Bit 0 is Monday; see FlashcardPlanSchedule. */
    @Column(name = "weekday_mask", nullable = false)
    private int weekdayMask;

    /** Null turns reminders off; the plan can still be studied from the page. */
    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Null when nothing is scheduled. The reminder job moves it, which must not conflict with a learner's edit. */
    @OptimisticLock(excluded = true)
    @Column(name = "next_reminder_at")
    private Instant nextReminderAt;

    /** Chosen lessons and cards as JSON. Plain references: deleted cards simply stop appearing. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String selection;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
