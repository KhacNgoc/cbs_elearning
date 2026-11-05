package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Flashcard;
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

    /** Counts per lesson of the cards a learner sees: published lesson cards and their own. */
    @Query("select card.lesson.id as lessonId, count(card) as total, "
            + "sum(case when card.owner is null then 0 else 1 end) as mine "
            + "from Flashcard card where card.course.id in :courseIds and card.status = 'PUBLISHED' "
            + "and (card.owner is null or card.owner.id = :userId) group by card.lesson.id")
    List<FlashcardLessonCountView> countByLesson(@Param("courseIds") Collection<String> courseIds,
            @Param("userId") Long userId);
}
