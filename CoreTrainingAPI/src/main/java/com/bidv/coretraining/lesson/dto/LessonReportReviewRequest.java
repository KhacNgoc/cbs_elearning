package com.bidv.coretraining.lesson.dto;

import com.bidv.coretraining.lesson.entity.LessonReport;

public record LessonReportReviewRequest(Long version, LessonReport.Status status, String resolution) {
}
