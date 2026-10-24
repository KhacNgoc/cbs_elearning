package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, String lessonId);

    @org.springframework.data.jpa.repository.Query("select p from LessonProgress p where p.user.id=:userId and p.lesson.course.id=:courseId")
    List<LessonProgress> findByUserIdAndCourseId(@org.springframework.data.repository.query.Param("userId") Long userId,
        @org.springframework.data.repository.query.Param("courseId") String courseId);

    List<LessonProgress> findByUserId(Long userId);

    boolean existsByLessonIdIn(java.util.Collection<String> lessonIds);
}
