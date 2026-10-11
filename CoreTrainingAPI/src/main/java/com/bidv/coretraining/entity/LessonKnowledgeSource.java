package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lesson_knowledge_sources", indexes = {
        @Index(name = "idx_lesson_knowledge_source_lesson", columnList = "lesson_id"),
        @Index(name = "idx_lesson_knowledge_source_document", columnList = "document_id")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LessonKnowledgeSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CourseDocument document;
    private Integer pageFrom;
    private Integer pageTo;
    @Column(length = 2000)
    private String section;
}
