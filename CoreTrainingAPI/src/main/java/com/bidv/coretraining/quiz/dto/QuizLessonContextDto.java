package com.bidv.coretraining.quiz.dto;

/** Scalar metadata for quiz editing; deliberately excludes lesson content. */
public record QuizLessonContextDto(String id, String title, String courseId,
        Integer quizPassingScore, Boolean quizRequired, Integer quizTimeLimitMinutes,
        Boolean quizShuffleQuestions, Boolean quizShuffleOptions) {}
