package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chapters", uniqueConstraints = @UniqueConstraint(columnNames = { "course_id", "code" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chapter {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String title;
    private String description;
    @Builder.Default
    private Integer chapterOrder = 0;
    @Builder.Default
    private String status = "DRAFT";
    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lessonOrder ASC, id ASC")
    @Builder.Default
    private java.util.List<Lesson> lessons = new java.util.ArrayList<>();
}
