package com.bidv.coretraining.rag.repository;

import com.bidv.coretraining.rag.entity.RagChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagChatMessageRepository extends JpaRepository<RagChatMessage, Long> {
    @org.springframework.data.jpa.repository.Query("select m.id from RagChatMessage m where m.user.id=:userId and m.course.id=:courseId and m.id<:beforeId order by m.id desc")
    List<Long> findHistoryIds(@org.springframework.data.repository.query.Param("userId") Long userId,@org.springframework.data.repository.query.Param("courseId") String courseId,@org.springframework.data.repository.query.Param("beforeId") Long beforeId,org.springframework.data.domain.Pageable page);
    @EntityGraph(attributePaths="sources")
    @org.springframework.data.jpa.repository.Query("select distinct m from RagChatMessage m where m.id in :ids")
    List<RagChatMessage> findHistoryMessages(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    @EntityGraph(attributePaths = "sources")
    List<RagChatMessage> findTop50ByUserIdAndCourseIdOrderByCreatedAtDesc(Long userId, String courseId);
}
