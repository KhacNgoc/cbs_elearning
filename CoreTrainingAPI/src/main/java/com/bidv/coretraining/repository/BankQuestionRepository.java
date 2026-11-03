package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.BankQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BankQuestionRepository extends JpaRepository<BankQuestion, Long> {
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("""
        select b.text from BankQuestion b
        where b.course.id = :courseId and b.status <> com.bidv.coretraining.entity.BankQuestion.Status.ARCHIVED
        order by b.updatedAt desc
        """)
    List<String> findRecentTextsByCourseId(@Param("courseId") String courseId, Pageable pageable);

    @Query("""
        select b.text from BankQuestion b
        where b.relatedLesson.id = :lessonId and b.status <> com.bidv.coretraining.entity.BankQuestion.Status.ARCHIVED
        order by b.updatedAt desc
        """)
    List<String> findRecentTextsByLessonId(@Param("lessonId") String lessonId, Pageable pageable);
}
