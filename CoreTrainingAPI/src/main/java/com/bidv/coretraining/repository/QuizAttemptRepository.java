package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, String> {
    java.util.List<QuizAttempt> findByUserIdOrderByTakenAtDesc(Long userId);
}
