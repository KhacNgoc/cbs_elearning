package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.LessonKnowledgeSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LessonKnowledgeSourceRepository extends JpaRepository<LessonKnowledgeSource, Long> {
    void deleteByDocumentId(String documentId);

    @EntityGraph(attributePaths = { "document", "document.course" })
    List<LessonKnowledgeSource> findByLessonIdOrderByIdAsc(String lessonId);
}
