package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, String> {
    java.util.List<QuizAttempt> findByUserIdOrderByTakenAtDesc(Long userId);

    @org.springframework.data.jpa.repository.Query("""
        select a from QuizAttempt a
        where a.user.id = :userId and a.quiz.lesson.id = :lessonId and a.score = :score and a.total = :total
          and a.takenAt between :from and :to
        order by a.takenAt
        """)
    java.util.List<QuizAttempt> findRecordedSnapshots(
        @org.springframework.data.repository.query.Param("userId") Long userId,
        @org.springframework.data.repository.query.Param("lessonId") String lessonId,
        @org.springframework.data.repository.query.Param("score") Integer score,
        @org.springframework.data.repository.query.Param("total") Integer total,
        @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
        @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
}
