package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;
    private Integer revision;
    private Integer score;
    private Integer total;
    private Integer passingScore;
    private boolean passed;
    private java.time.LocalDateTime takenAt;
    @Column(columnDefinition = "TEXT")
    private String answersJson;
    @Column(columnDefinition = "TEXT")
    private String questionsJson;
}
