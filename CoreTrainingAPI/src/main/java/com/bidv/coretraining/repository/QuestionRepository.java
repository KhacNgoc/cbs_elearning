package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    @org.springframework.data.jpa.repository.Query(value = """
        select new com.bidv.coretraining.dto.QuizSummaryDto(c.id, c.name, l.id, l.title,
            count(q.id), coalesce(k.passingScore, c.quizPassingScore, 80))
        from Question q join q.course c left join q.lesson l left join l.quiz k
        where c.id in :ids and
            lower(concat(concat(concat(coalesce(l.title, 'Bài kiểm tra tổng hợp'), ' '), c.name), concat(' ', coalesce(l.id, '')))) like :pattern escape '!'
        group by q.quiz.id, c.id, c.name, l.id, l.title, k.passingScore, c.quizPassingScore
        order by c.name, c.id, l.title, l.id
        """, countQuery = """
        select count(distinct q.quiz.id) from Question q join q.course c left join q.lesson l
        where c.id in :ids and
            lower(concat(concat(concat(coalesce(l.title, 'Bài kiểm tra tổng hợp'), ' '), c.name), concat(' ', coalesce(l.id, '')))) like :pattern escape '!'
        """)
    org.springframework.data.domain.Page<com.bidv.coretraining.dto.QuizSummaryDto> findQuizSummaries(
        @org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids,
        @org.springframework.data.repository.query.Param("pattern") String pattern,
        org.springframework.data.domain.Pageable pageable);

    List<Question> findByCourseIdAndLessonIsNull(String courseId);

    List<Question> findByLessonId(String lessonId);

    @org.springframework.data.jpa.repository.Query("select q.text from Question q where q.lesson.id = :lessonId")
    List<String> findTextsByLessonId(@org.springframework.data.repository.query.Param("lessonId") String lessonId);

    @org.springframework.data.jpa.repository.Query("select q.text from Question q where q.course.id = :courseId and q.lesson is null")
    List<String> findCourseQuizTexts(@org.springframework.data.repository.query.Param("courseId") String courseId);

    @org.springframework.data.jpa.repository.Query("select distinct q.course.id from Question q where q.course.id in :ids and q.lesson is null")
    java.util.Set<String> findCourseIdsWithQuiz(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

    boolean existsByCourseIdAndLessonIsNull(String courseId);

    boolean existsByLessonId(String lessonId);

    void deleteByLessonId(String lessonId);

    void deleteByCourseIdAndLessonIsNull(String courseId);
}
