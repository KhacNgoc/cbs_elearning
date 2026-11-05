package com.bidv.coretraining.lesson.entity;
import com.bidv.coretraining.enums.VideoSource;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lesson_contents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonContent {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    @Version
    private Long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson;
    @Column(columnDefinition = "TEXT")
    private String textBody;
    @Column(columnDefinition = "TEXT")
    private String videoUrl;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VideoSource videoSource = VideoSource.URL;
}
