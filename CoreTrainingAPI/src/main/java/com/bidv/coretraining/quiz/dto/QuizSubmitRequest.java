package com.bidv.coretraining.quiz.dto;

import lombok.Data;

import java.util.Map;

@Data
public class QuizSubmitRequest {
    // Map of questionId -> selected option indexes
    private Map<Long, java.util.List<Integer>> selectedAnswers;
    private Integer timeElapsed;
    /** Required for timed quizzes: the id returned by the start endpoint. */
    private String sessionId;
}
