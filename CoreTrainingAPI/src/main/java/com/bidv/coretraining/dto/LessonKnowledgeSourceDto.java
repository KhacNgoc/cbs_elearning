package com.bidv.coretraining.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LessonKnowledgeSourceDto(
                @NotBlank String documentId,
                @Min(1) Integer pageFrom,
                @Min(1) Integer pageTo,
                @Size(max = 2000) String section) {
}
