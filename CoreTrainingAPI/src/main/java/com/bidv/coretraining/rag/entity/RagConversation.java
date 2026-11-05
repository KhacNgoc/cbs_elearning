package com.bidv.coretraining.rag.entity;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rag_conversations", indexes = {
        @Index(name = "idx_rag_conversation_owner_course_updated", columnList = "user_id,course_id,updated_at"),
        @Index(name = "idx_rag_conversation_owner_updated", columnList = "user_id,updated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagConversation {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Course the conversation started from; null for cross-course conversations. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "last_summarized_message_id")
    private Long lastSummarizedMessageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
