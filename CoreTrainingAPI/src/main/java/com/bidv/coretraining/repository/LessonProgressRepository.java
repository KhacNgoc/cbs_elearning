package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, String lessonId);
    List<LessonProgress> findByUserId(Long userId);
    boolean existsByLessonIdIn(java.util.Collection<String> lessonIds);
}
