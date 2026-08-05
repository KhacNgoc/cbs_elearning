package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgressDto {
    private String lessonId;
    private Double lastPositionSeconds;
    private Double watchedSeconds;
    private Double durationSeconds;
}
