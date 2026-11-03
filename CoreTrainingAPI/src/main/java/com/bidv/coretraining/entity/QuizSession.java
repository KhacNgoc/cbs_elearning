package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Server-side clock for one timed quiz attempt; the deadline is fixed when the attempt starts. */
@Entity
@Table(name = "quiz_sessions", indexes = @Index(name = "idx_quiz_sessions_user_lesson", columnList = "user_id, lesson_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSession {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
    @Column(nullable = false)
    private LocalDateTime startedAt;
    @Column(nullable = false)
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
}
