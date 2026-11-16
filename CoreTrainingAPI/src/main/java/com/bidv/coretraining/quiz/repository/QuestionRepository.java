package com.bidv.coretraining.quiz.repository;

import com.bidv.coretraining.quiz.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    /**
     * One row per quiz that has questions, read from the quizzes table so the page and its count do not
     * aggregate every question first. Columns: quiz id, course id, course name, lesson id, lesson title,
     * passing score. {@code allCourses} skips the course scope (system admins); otherwise {@code ids}
     * holds the courses the user may read.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select k.id, c.id, c.name, l.id, l.title, coalesce(k.passingScore, c.quizPassingScore, 80)
        from Quiz k join k.lesson l join l.course c
        where (:allCourses = true or c.id in :ids)
            and (:courseId is null or c.id = :courseId)
            and (:pattern = '%%' or lower(concat(concat(concat(coalesce(l.title, ''), ' '), c.name), concat(' ', l.id))) like :pattern escape '!')
            and exists (select 1 from Question q where q.quiz = k)
        order by c.name, c.id, l.title, l.id
        """, countQuery = """
        select count(k) from Quiz k join k.lesson l join l.course c
        where (:allCourses = true or c.id in :ids)
            and (:courseId is null or c.id = :courseId)
            and (:pattern = '%%' or lower(concat(concat(concat(coalesce(l.title, ''), ' '), c.name), concat(' ', l.id))) like :pattern escape '!')
            and exists (select 1 from Question q where q.quiz = k)
        """)
    org.springframework.data.domain.Page<Object[]> findQuizSummaryPage(
        @org.springframework.data.repository.query.Param("allCourses") boolean allCourses,
        @org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids,
        @org.springframework.data.repository.query.Param("courseId") String courseId,
        @org.springframework.data.repository.query.Param("pattern") String pattern,
        org.springframework.data.domain.Pageable pageable);

    /** Question count per quiz, for the quizzes shown on one page. Rows: quiz id, count. */
    @org.springframework.data.jpa.repository.Query("select q.quiz.id, count(q) from Question q where q.quiz.id in :quizIds group by q.quiz.id")
    List<Object[]> countByQuizIds(@org.springframework.data.repository.query.Param("quizIds") java.util.Collection<String> quizIds);

    List<Question> findByCourseIdAndLessonIsNullOrderByQuestionOrderAscIdAsc(String courseId);

    List<Question> findByLessonIdOrderByQuestionOrderAscIdAsc(String lessonId);

    /** Order for a question appended to the quiz: one past the last, or 0 for an empty quiz. */
    @org.springframework.data.jpa.repository.Query("select coalesce(max(q.questionOrder) + 1, 0) from Question q where q.quiz.id = :quizId")
    int nextQuestionOrder(@org.springframework.data.repository.query.Param("quizId") String quizId);

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
