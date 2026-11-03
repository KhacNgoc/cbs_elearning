package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface QuizSessionRepository extends JpaRepository<QuizSession, String> {
    Optional<QuizSession> findFirstByUserIdAndLessonIdAndSubmittedAtIsNullAndDeadlineAtAfterOrderByStartedAtDesc(
            Long userId, String lessonId, LocalDateTime now);
}
