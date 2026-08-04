package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.RagChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagChatMessageRepository extends JpaRepository<RagChatMessage, Long> {
    @EntityGraph(attributePaths = "sources")
    List<RagChatMessage> findTop50ByUserIdAndTopicIdOrderByCreatedAtDesc(Long userId, String topicId);
}
