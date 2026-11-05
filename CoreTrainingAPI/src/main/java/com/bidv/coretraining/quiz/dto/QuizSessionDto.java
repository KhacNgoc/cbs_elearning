package com.bidv.coretraining.quiz.dto;

/**
 * Attempt clock returned when a learner opens a quiz. All fields are null for an untimed quiz.
 * remainingSeconds is computed on the server so the client never depends on its own clock.
 */
public record QuizSessionDto(String sessionId, Integer timeLimitMinutes, Long remainingSeconds) {}
