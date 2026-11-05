package com.bidv.coretraining.quiz.dto;

import java.util.*;

/** Data contracts for LearnerQuizEntryService. */
public final class LearnerQuizEntryDto {
    private LearnerQuizEntryDto() {}

    public record Context(String id,String name,String lessonId,Integer quizPassingScore,Integer score,Integer total,Integer timeLimitMinutes) {}
}
