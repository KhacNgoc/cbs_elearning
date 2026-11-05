package com.bidv.coretraining.rag.service;

import com.bidv.coretraining.rag.dto.RagChatDto.*;
import com.bidv.coretraining.rag.dto.RagChatHistoryDto;
import com.bidv.coretraining.rag.dto.RagChatRequest;
import com.bidv.coretraining.rag.dto.RagChatResponse;
import java.util.*;

/** Business operations for RagChatService. */
public interface RagChatService {
    public RagChatResponse chat(String username, RagChatRequest request);

    public List<RagChatHistoryDto> history(String username,String courseId,String conversationId);

    public HistoryPage historyPage(String username,String courseId,String conversationId,Long beforeId,int size);
}
