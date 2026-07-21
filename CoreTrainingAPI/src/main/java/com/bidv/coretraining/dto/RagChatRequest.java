package com.bidv.coretraining.dto;

import jakarta.validation.constraints.NotBlank;

public record RagChatRequest(@NotBlank String question, String courseId, Integer page, Integer limit) {}
