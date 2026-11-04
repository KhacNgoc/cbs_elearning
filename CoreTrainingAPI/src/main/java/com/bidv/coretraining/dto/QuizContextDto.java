package com.bidv.coretraining.dto;

import com.bidv.coretraining.dto.QuizLessonContextDto;
import java.util.*;

/** Data contracts for QuizContextService. */
public final class QuizContextDto {
    private QuizContextDto() {}

    public record Context(String id, String name, String domainName, String status,
            Integer quizPassingScore, Map<String, Set<String>> adminActions,
            List<QuizLessonContextDto> lessons) {}
}
