package com.bidv.coretraining.rag.repository;

import com.bidv.coretraining.rag.entity.RagConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagConversationRepository extends JpaRepository<RagConversation, String> {
    Optional<RagConversation> findFirstByUserIdAndCourseIdOrderByUpdatedAtDesc(Long userId, String courseId);

    Optional<RagConversation> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);
}
