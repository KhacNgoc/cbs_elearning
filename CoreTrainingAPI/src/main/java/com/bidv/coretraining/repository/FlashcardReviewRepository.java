package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.FlashcardReview;
import com.bidv.coretraining.repository.projection.FlashcardCourseDueView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, Long> {
    @Query("select review from FlashcardReview review where review.user.id = :userId "
            + "and review.flashcard.lesson.id = :lessonId")
    List<FlashcardReview> findForLesson(@Param("userId") Long userId, @Param("lessonId") String lessonId);

    Optional<FlashcardReview> findByUserIdAndFlashcardId(Long userId, Long flashcardId);

    /** Due cards per course, limited to content learners can currently see. */
    @Query("select course.id as courseId, course.name as courseName, count(review) as due "
            + "from FlashcardReview review join review.flashcard card join card.lesson lesson "
            + "join lesson.chapter chapter join card.course course "
            + "where review.user.id = :userId and review.dueAt <= :now and card.status = 'PUBLISHED' "
            + "and lesson.status = 'PUBLISHED' and chapter.status = 'PUBLISHED' and course.status = 'PUBLISHED' "
            + "group by course.id, course.name order by count(review) desc")
    List<FlashcardCourseDueView> dueByCourse(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("select review from FlashcardReview review join fetch review.flashcard card join fetch card.lesson lesson "
            + "join lesson.chapter chapter where review.user.id = :userId and card.course.id = :courseId "
            + "and review.dueAt <= :now and card.status = 'PUBLISHED' and lesson.status = 'PUBLISHED' "
            + "and chapter.status = 'PUBLISHED' order by review.dueAt asc, review.id asc")
    List<FlashcardReview> findDueInCourse(@Param("userId") Long userId, @Param("courseId") String courseId,
            @Param("now") LocalDateTime now, Pageable page);

    @Modifying
    @Query("delete from FlashcardReview review where review.flashcard.id = :flashcardId")
    void deleteByFlashcardId(@Param("flashcardId") Long flashcardId);
}
