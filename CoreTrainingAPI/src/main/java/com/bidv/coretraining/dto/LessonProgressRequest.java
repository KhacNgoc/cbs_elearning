package com.bidv.coretraining.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class LessonProgressRequest {
    @PositiveOrZero
    private Double lastPositionSeconds;

    @PositiveOrZero
    private Double watchedSeconds;

    @PositiveOrZero
    private Double durationSeconds;
    @jakarta.validation.constraints.Pattern(regexp = "VIDEO|DOCUMENT|TEXT")
    private String contentType;
    private Long documentId;
    @jakarta.validation.constraints.Positive
    private Integer documentPage;
    @PositiveOrZero
    private Double scrollOffset;
}
