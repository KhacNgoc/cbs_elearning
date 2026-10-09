package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByCourseIdAndLessonIsNull(String courseId);
    List<Question> findByLessonId(String lessonId);
    boolean existsByCourseIdAndLessonIsNull(String courseId);
    boolean existsByLessonId(String lessonId);
    void deleteByLessonId(String lessonId);
    void deleteByCourseIdAndLessonIsNull(String courseId);
}
