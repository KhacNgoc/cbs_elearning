package com.bidv.coretraining.dto;

import lombok.Data;

import java.util.Map;

@Data
public class QuizSubmitRequest {
    // Map of questionId -> answerIndex
    private Map<Long, Integer> selectedAnswers;
    private Integer timeElapsed;
}
