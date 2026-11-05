package com.bidv.coretraining.quiz.repository;

import java.util.Optional;

/** Targeted score projection after the service has authorized the course/lesson. */
public interface LearnerQuizEntryRepository {
    record Score(Integer score, Integer total) {}
    Optional<Score> latestScore(Long userId, String courseId, String lessonId);
}
