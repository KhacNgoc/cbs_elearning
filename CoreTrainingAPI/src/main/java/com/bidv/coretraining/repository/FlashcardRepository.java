package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByLessonIdOrderByCardOrderAscIdAsc(String lessonId);

    List<Flashcard> findByLessonIdAndStatusOrderByCardOrderAscIdAsc(String lessonId, String status);

    Optional<Flashcard> findByIdAndLessonId(Long id, String lessonId);

    List<Flashcard> findByLessonIdAndIdIn(String lessonId, Collection<Long> ids);

    @Query("select coalesce(max(card.cardOrder), -1) from Flashcard card where card.lesson.id = :lessonId")
    int maxCardOrder(@Param("lessonId") String lessonId);
}
