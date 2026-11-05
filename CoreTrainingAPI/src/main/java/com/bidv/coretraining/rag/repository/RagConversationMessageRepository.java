package com.bidv.coretraining.rag.repository;

import com.bidv.coretraining.rag.entity.RagConversationMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagConversationMessageRepository extends JpaRepository<RagConversationMessage, Long> {
    // Select complete exchange IDs before fetching source collections; never paginate a collection join.
    @org.springframework.data.jpa.repository.Query("""
      select a.id,u.id from RagConversationMessage a,RagConversationMessage u
      where a.conversation.id=:conversationId and a.role='assistant' and a.id<:beforeId
        and u.conversation.id=a.conversation.id and u.role='user'
        and u.id=(select max(q.id) from RagConversationMessage q where q.conversation.id=a.conversation.id and q.role='user' and q.id<a.id)
        and not exists(select prior.id from RagConversationMessage prior where prior.conversation.id=a.conversation.id and prior.role='assistant' and prior.id>u.id and prior.id<a.id)
      order by a.id desc
      """)
    List<Object[]> findExchangeIds(@org.springframework.data.repository.query.Param("conversationId") String conversationId,
        @org.springframework.data.repository.query.Param("beforeId") Long beforeId,org.springframework.data.domain.Pageable pageable);
    @EntityGraph(attributePaths="sources")
    @org.springframework.data.jpa.repository.Query("select distinct m from RagConversationMessage m where m.id in :ids")
    List<RagConversationMessage> findHistoryMessages(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    List<RagConversationMessage> findTop30ByConversationIdAndIdLessThanOrderByIdDesc(String conversationId, Long id);

    List<RagConversationMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(String conversationId, Long id);

    @EntityGraph(attributePaths = "sources")
    List<RagConversationMessage> findByConversationIdOrderByIdAsc(String conversationId);
}
