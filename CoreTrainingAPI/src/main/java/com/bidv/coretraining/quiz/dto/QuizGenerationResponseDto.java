package com.bidv.coretraining.quiz.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuizGenerationResponseDto {
    private List<GeneratedQuizQuestionDto> questions = new ArrayList<>();
    private List<QuizGenerationSourceDto> sources = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
