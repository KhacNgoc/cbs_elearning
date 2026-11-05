package com.bidv.coretraining.rag.dto;

import java.util.List;

public record RagChatResponse(
                String answer,
                boolean grounded,
                List<RagSourceDto> sources,
                String conversationId,
                boolean needsClarification) {
}
