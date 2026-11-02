package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.LessonReport;

public record LessonReportReviewRequest(Long version, LessonReport.Status status, String resolution) {
}
