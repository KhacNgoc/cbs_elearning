package com.bidv.coretraining.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuizGenerationRequestDto {
    @NotBlank
    private String courseId;
    private String lessonId;
    @Min(0)
    @Max(10)
    private int singleChoiceCount = 5;
    @Min(0)
    @Max(10)
    private int multipleChoiceCount = 0;
    @Min(3)
    @Max(6)
    private int optionCount = 4;
}
