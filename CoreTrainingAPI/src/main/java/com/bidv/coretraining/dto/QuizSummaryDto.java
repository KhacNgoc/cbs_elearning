package com.bidv.coretraining.dto;

public record QuizSummaryDto(String courseId, String courseName, String lessonId,
        String lessonTitle, Long questionCount, Integer passingScore) {}
