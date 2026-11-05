package com.bidv.coretraining.quiz.entity;

import com.bidv.coretraining.lesson.entity.Lesson;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    @Version
    private Long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;
    private String title;
    @Builder.Default
    @Column(nullable = false)
    private Integer passingScore = 80;
    @Builder.Default
    @Column(nullable = false)
    private boolean requiredForProgress = true;
    /** Minutes allowed per attempt; null means no time limit. */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;
    @Builder.Default
    private Integer revision = 1;
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private java.util.List<Question> questions = new java.util.ArrayList<>();
}
