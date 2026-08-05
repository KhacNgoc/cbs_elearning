package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.RagConversationMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagConversationMessageRepository extends JpaRepository<RagConversationMessage, Long> {
    List<RagConversationMessage> findTop30ByConversationIdAndIdLessThanOrderByIdDesc(String conversationId, Long id);

    List<RagConversationMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(String conversationId, Long id);

    @EntityGraph(attributePaths = "sources")
    List<RagConversationMessage> findByConversationIdOrderByIdAsc(String conversationId);
}
