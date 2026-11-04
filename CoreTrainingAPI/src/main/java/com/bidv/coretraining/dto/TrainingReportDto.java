package com.bidv.coretraining.dto;

import java.time.*;
import java.util.*;

/** Data contracts for TrainingReportService. */
public final class TrainingReportDto {
    private TrainingReportDto() {}

    public record Attempt(double score, LocalDateTime takenAt, int passingScore, String assessment) {}

    public record Row(String id, String learnerId, String name, String unit, String group,
                      String courseId, String courseName, String program, String domain,
                      int progress, boolean mandatory, LocalDate dueDate, LocalDate completedAt,
                      boolean completed, boolean started, List<Attempt> attempts,
                      Double assessmentAverage, boolean assessmentPassed, boolean assessmentFirstPassed,
                      int passingScore) {}

    public record Snapshot(LocalDate asOf, List<Row> rows) {}
}
