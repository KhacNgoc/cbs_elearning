package com.bidv.coretraining.repository;
import com.bidv.coretraining.repository.projection.LessonOutlineView;
import com.bidv.coretraining.repository.projection.LessonAccessView;

import com.bidv.coretraining.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, String> {
  @org.springframework.data.jpa.repository.Query("select l.course.id from Lesson l where l.id = :id")
  java.util.Optional<String> findQuizCourseId(@org.springframework.data.repository.query.Param("id") String id);

  @org.springframework.data.jpa.repository.Query("""
      select new com.bidv.coretraining.dto.QuizLessonContextDto(l.id, l.title, l.course.id,
        coalesce(q.passingScore, 80), coalesce(q.requiredForProgress, true))
      from Lesson l join l.chapter ch left join l.quiz q
      where l.course.id = :courseId and (:lessonId is null or l.id = :lessonId)
        and l.status <> 'DELETED' and ch.status <> 'DELETED'
      order by ch.chapterOrder, ch.id, l.lessonOrder, l.id
      """)
  List<com.bidv.coretraining.dto.QuizLessonContextDto> findQuizContext(
      @org.springframework.data.repository.query.Param("courseId") String courseId,
      @org.springframework.data.repository.query.Param("lessonId") String lessonId);

  @org.springframework.data.jpa.repository.Query("""
      select l.id as id, l.title as title, l.course.id as courseId, ch.id as chapterId, ch.status as chapterStatus,
        l.status as status, l.version as version, l.lessonOrder as lessonOrder,
        case when count(q.id) > 0 then true else false end as hasQuiz,
        coalesce(quiz.requiredForProgress, false) as quizRequired,
        coalesce(quiz.passingScore, 80) as quizPassingScore,
        case when length(trim(content.videoUrl)) > 0 then true else false end as hasVideo,
        case when length(trim(content.textBody)) > 0 then true else false end as hasText
      from Lesson l join l.chapter ch left join l.content content left join l.quiz quiz
        left join quiz.questions q
      where l.course.id in :courseIds and (:includeDraft = true or (ch.status = 'PUBLISHED' and l.status = 'PUBLISHED'))
      group by ch.chapterOrder, l.id, l.title, l.course.id, ch.id, ch.status, l.status, l.version, l.lessonOrder,
        quiz.requiredForProgress, quiz.passingScore, content.videoUrl, content.textBody
      order by ch.chapterOrder, ch.id, l.lessonOrder, l.id
      """)
  List<LessonOutlineView> findOutlines(
      @org.springframework.data.repository.query.Param("courseIds") java.util.Collection<String> courseIds,
      @org.springframework.data.repository.query.Param("includeDraft") boolean includeDraft);

  default List<LessonOutlineView> findOutline(String courseId, boolean includeDraft) {
    return findOutlines(List.of(courseId), includeDraft);
  }

  @org.springframework.data.jpa.repository.Query("""
      select l.id as id, l.course.id as courseId, ch.id as chapterId, l.status as status, l.version as version,
        ch.status as chapterStatus, l.lessonOrder as lessonOrder, ch.chapterOrder as chapterOrder,
        case when length(trim(content.textBody)) > 0 then true else false end as hasText,
        case when length(trim(content.videoUrl)) > 0 then true else false end as hasVideo
      from Lesson l join l.chapter ch left join l.content content where l.id = :id
      """)
  java.util.Optional<LessonAccessView> findAccess(@org.springframework.data.repository.query.Param("id") String id);

  @org.springframework.data.jpa.repository.Query("""
      select l.id as id,
        case when length(trim(content.textBody)) > 0 then true else false end as hasText,
        case when length(trim(content.videoUrl)) > 0 then true else false end as hasVideo
      from Lesson l left join l.content content where l.id in :ids
      """)
  List<LessonAccessView> findProgressRequirements(
      @org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

  @org.springframework.data.jpa.repository.Query("""
      select count(ch) > 0 from Chapter ch
      where ch.course.id = :courseId and ch.status = 'PUBLISHED' and (
        ((ch.chapterOrder < :chapterOrder or (ch.chapterOrder = :chapterOrder and ch.id < :chapterId)) and (
          not exists (select p.id from Lesson p where p.chapter = ch and p.status = 'PUBLISHED')
          or exists (select p.id from Lesson p where p.chapter = ch and p.status = 'PUBLISHED'
            and not exists (select done.id from User u join u.completedLessons done where u.id = :userId and done.id = p.id))
        ))
        or (ch.id = :chapterId and exists (
          select p.id from Lesson p where p.chapter = ch and p.status = 'PUBLISHED'
            and (p.lessonOrder < :lessonOrder or (p.lessonOrder = :lessonOrder and p.id < :lessonId))
            and not exists (select done.id from User u join u.completedLessons done where u.id = :userId and done.id = p.id)
        ))
      )
      """)
  boolean hasIncompletePredecessors(@org.springframework.data.repository.query.Param("courseId") String courseId,
      @org.springframework.data.repository.query.Param("chapterOrder") int chapterOrder,
      @org.springframework.data.repository.query.Param("chapterId") String chapterId,
      @org.springframework.data.repository.query.Param("lessonOrder") int lessonOrder,
      @org.springframework.data.repository.query.Param("lessonId") String lessonId,
      @org.springframework.data.repository.query.Param("userId") Long userId);

  @org.springframework.data.jpa.repository.Query("select distinct l from Lesson l left join l.documents d where d.fileUrl=:path or l.content.videoUrl=:path")
  java.util.List<Lesson> findByAssetPath(@org.springframework.data.repository.query.Param("path") String path);

  List<Lesson> findByChapterIdOrderByLessonOrderAscIdAsc(String chapterId);

  List<Lesson> findByCourseIdOrderByLessonOrderAscIdAsc(String courseId);
}
