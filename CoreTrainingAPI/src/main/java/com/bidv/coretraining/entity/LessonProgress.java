package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_progress", uniqueConstraints = @UniqueConstraint(name = "uk_lesson_progress_user_lesson", columnNames = {
        "user_id", "lesson_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "last_position_seconds", nullable = false)
    @Builder.Default
    private Double lastPositionSeconds = 0D;

    @Column(name = "watched_seconds", nullable = false)
    @Builder.Default
    private Double watchedSeconds = 0D;

    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private Double durationSeconds = 0D;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    private String contentType;
    private Long documentId;
    private Integer documentPage;
    private Double scrollOffset;

    public record DocumentPosition(Integer page, Double scrollOffset) {
    }

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "document_positions")
    @Builder.Default
    private java.util.Map<String, DocumentPosition> documentPositions = new java.util.LinkedHashMap<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean contentCompleted = false;
}
