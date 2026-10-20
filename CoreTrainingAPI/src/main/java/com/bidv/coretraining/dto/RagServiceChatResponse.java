package com.bidv.coretraining.dto;

import java.util.List;

public record RagServiceChatResponse(
                String answer,
                boolean grounded,
                List<RagSourceDto> sources,
                String standaloneQuestion,
                boolean needsClarification,
                String rewriteReason) {
}
