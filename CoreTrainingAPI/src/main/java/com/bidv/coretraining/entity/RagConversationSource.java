package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rag_conversation_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagConversationSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private RagConversationMessage message;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(nullable = false)
    private Integer page;

    @Column(nullable = false)
    private String section;

    @Column(name = "course_id")
    private String courseId;

    @Column(name = "course_name", length = 500)
    private String courseName;
}
