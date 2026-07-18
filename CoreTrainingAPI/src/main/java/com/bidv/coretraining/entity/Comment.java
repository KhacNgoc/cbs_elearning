package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    private String id; // e.g. "comment_12345678"

    @Column(nullable = false)
    private String username; // We store the username or map to user

    @Column(nullable = false)
    private String avatar; // Initial character of username

    @Column(nullable = false)
    private Integer rating; // 1 to 5 stars

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
}
