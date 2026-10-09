package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSettingsDto {
    private String courseId;
    private String lessonId;
    private Integer passingScore;
    private Boolean requiredForProgress;
}
