package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.RagChatDto.*;
import com.bidv.coretraining.dto.*;
import java.util.*;

/** Business operations for RagChatService. */
public interface RagChatService {
    public RagChatResponse chat(String username, RagChatRequest request);

    public List<RagChatHistoryDto> history(String username,String courseId,String conversationId);

    public HistoryPage historyPage(String username,String courseId,String conversationId,Long beforeId,int size);
}
