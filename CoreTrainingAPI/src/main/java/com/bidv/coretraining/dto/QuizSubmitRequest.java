package com.bidv.coretraining.dto;

import lombok.Data;

import java.util.Map;

@Data
public class QuizSubmitRequest {
    // Map of questionId -> selected option indexes
    private Map<Long, java.util.List<Integer>> selectedAnswers;
    private Integer timeElapsed;
}
