package com.bidv.coretraining.dto;

/** Scalar metadata for quiz editing; deliberately excludes lesson content. */
public record QuizLessonContextDto(String id, String title, String courseId,
        Integer quizPassingScore, Boolean quizRequired) {}
