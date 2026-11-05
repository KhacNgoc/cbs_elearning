package com.bidv.coretraining.rag.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rag_conversation_messages", indexes = {
        @Index(name = "idx_rag_conversation_message_order", columnList = "conversation_id,id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private RagConversation conversation;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Boolean grounded;

    @Column(name = "original_question", columnDefinition = "TEXT")
    private String originalQuestion;

    @Column(name = "standalone_question", columnDefinition = "TEXT")
    private String standaloneQuestion;

    @Column(name = "needs_clarification")
    private Boolean needsClarification;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<RagConversationSource> sources = new ArrayList<>();

    public void addSource(RagConversationSource source) {
        source.setMessage(this);
        sources.add(source);
    }
}
