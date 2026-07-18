package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_quiz_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuizScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private Integer total;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;
}
