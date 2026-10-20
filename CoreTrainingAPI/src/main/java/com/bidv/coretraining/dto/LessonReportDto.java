package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.LessonReport;
import java.time.LocalDateTime;
import java.util.List;

public record LessonReportDto(Long id, Long version, String lessonId, String lessonTitle, String courseId,
                String courseName,
                String reporterName, String content, List<String> images, LessonReport.Status status, String resolution,
                String reviewerName, LocalDateTime createdAt, LocalDateTime reviewedAt, boolean canReview) {
}
