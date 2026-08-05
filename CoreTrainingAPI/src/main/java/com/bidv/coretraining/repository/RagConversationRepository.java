package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.RagConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagConversationRepository extends JpaRepository<RagConversation, String> {
    Optional<RagConversation> findFirstByUserIdAndTopicIdOrderByUpdatedAtDesc(Long userId, String topicId);
}
