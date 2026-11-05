package com.bidv.coretraining.quiz.repository;

import com.bidv.coretraining.quiz.entity.UserQuizScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserQuizScoreRepository extends JpaRepository<UserQuizScore, Long> {
    List<UserQuizScore> findByUserId(Long userId);

    Optional<UserQuizScore> findByUserIdAndCourseIdAndLessonIsNull(Long userId, String courseId);

    Optional<UserQuizScore> findByUserIdAndLessonId(Long userId, String lessonId);
}
