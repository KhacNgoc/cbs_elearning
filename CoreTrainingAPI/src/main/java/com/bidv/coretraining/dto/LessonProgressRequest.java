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
}
