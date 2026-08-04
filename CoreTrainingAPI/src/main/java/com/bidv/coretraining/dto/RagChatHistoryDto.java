package com.bidv.coretraining.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RagChatHistoryDto(
        Long id,
        String courseId,
        String question,
        String answer,
        boolean grounded,
        List<RagSourceDto> sources,
        LocalDateTime createdAt
) {}
