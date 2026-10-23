package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.LessonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LessonDocumentRepository extends JpaRepository<LessonDocument, Long> {
    interface IdOnly { Long getId(); }
    boolean existsByIdAndLessonId(Long id, String lessonId);
    Optional<IdOnly> findFirstByLessonIdOrderByDocumentOrderAscIdAsc(String lessonId);
}
