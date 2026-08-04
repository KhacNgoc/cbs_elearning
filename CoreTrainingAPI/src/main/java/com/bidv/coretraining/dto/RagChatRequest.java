package com.bidv.coretraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagChatRequest(
        @NotBlank @Size(max = 4000) String question,
        @NotBlank String courseId,
        Integer page
) {}
