package com.bidv.coretraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code courseId} is optional context (the course being viewed). The assistant
 * always searches every course the learner can access and only favours it.
 */
public record RagChatRequest(
                @NotBlank @Size(max = 4000) String question,
                @Size(max = 255) String courseId,
                Integer page,
                String conversationId) {
}
