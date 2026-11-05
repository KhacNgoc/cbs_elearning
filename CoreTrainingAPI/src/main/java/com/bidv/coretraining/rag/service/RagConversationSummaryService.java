package com.bidv.coretraining.rag.service;

/** Business operations for RagConversationSummaryService. */
public interface RagConversationSummaryService {
    public void updateAsync(String conversationId, Long userId, String courseId);
}
