package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Flashcard;
import com.bidv.coretraining.repository.projection.FlashcardCardView;
import com.bidv.coretraining.repository.projection.FlashcardLessonCountView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Lesson-card queries filter on owner IS NULL so learners' personal cards never reach admins. */
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByLessonIdAndOwnerIsNullOrderByCardOrderAscIdAsc(String lessonId);

    List<Flashcard> findByLessonIdAndOwnerIsNullAndStatusOrderByCardOrderAscIdAsc(String lessonId, String status);

    Optional<Flashcard> findByIdAndLessonIdAndOwnerIsNull(Long id, String lessonId);

    List<Flashcard> findByLessonIdAndOwnerIsNullAndIdIn(String lessonId, Collection<Long> ids);

    @Query("select coalesce(max(card.cardOrder), -1) from Flashcard card where card.lesson.id = :lessonId "
            + "and card.owner is null")
    int maxCardOrder(@Param("lessonId") String lessonId);

    List<Flashcard> findByLessonIdAndOwnerIdOrderByCreatedAtAscIdAsc(String lessonId, Long ownerId);

    Optional<Flashcard> findByIdAndOwnerId(Long id, Long ownerId);

    long countByLessonIdAndOwnerId(String lessonId, Long ownerId);

    /** The cards a learner sees in these lessons, as scalars: loading Lesson entities costs queries per card. */
    @Query("select card.id as id, card.course.id as courseId, lesson.id as lessonId, lesson.title as lessonTitle, "
            + "card.type as type, card.front as front, card.back as back, "
            + "card.sourceDocumentId as sourceDocumentId, card.sourceDocumentName as sourceDocumentName, "
            + "card.sourcePage as sourcePage, card.sourceSection as sourceSection, "
            + "card.owner.id as ownerId, card.version as version "
            + "from Flashcard card join card.lesson lesson "
            + "where lesson.id in :lessonIds and card.status = 'PUBLISHED' "
            + "and (card.owner is null or card.owner.id = :userId) "
            + "order by case when card.owner is null then 0 else 1 end, card.cardOrder, card.createdAt, card.id")
    List<FlashcardCardView> findVisibleInLessons(@Param("lessonIds") Collection<String> lessonIds,
            @Param("userId") Long userId);

    /** Counts per lesson of the cards a learner sees: published lesson cards and their own. */
    @Query("select card.lesson.id as lessonId, count(card) as total, "
            + "sum(case when card.owner is null then 0 else 1 end) as mine "
            + "from Flashcard card where card.course.id in :courseIds and card.status = 'PUBLISHED' "
            + "and (card.owner is null or card.owner.id = :userId) group by card.lesson.id")
    List<FlashcardLessonCountView> countByLesson(@Param("courseIds") Collection<String> courseIds,
            @Param("userId") Long userId);
}
