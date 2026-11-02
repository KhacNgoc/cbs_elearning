package com.bidv.coretraining.dto;

import com.bidv.coretraining.dto.QuizSummaryDto;
import java.util.*;

/** Data contracts for QuizSummaryService. */
public final class QuizSummaryPageDto {
    private QuizSummaryPageDto() {}

    public record CourseOption(String id, String name) {}

    public record Result(List<QuizSummaryDto> content, long totalElements, int totalPages,
                         List<CourseOption> courses) {}
}
