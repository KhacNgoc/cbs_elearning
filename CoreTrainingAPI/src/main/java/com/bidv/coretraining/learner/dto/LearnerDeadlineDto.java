package com.bidv.coretraining.learner.dto;

import java.time.LocalDate;

public final class LearnerDeadlineDto {
    private LearnerDeadlineDto() {}

    public record Deadline(String courseId, String courseName, LocalDate dueDate, LocalDate defaultDate,
            boolean overridden) {}

    public record Request(String courseId, LocalDate dueDate) {}
}
