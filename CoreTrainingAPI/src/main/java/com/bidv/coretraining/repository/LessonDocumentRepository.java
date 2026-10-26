package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.LessonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LessonDocumentRepository extends JpaRepository<LessonDocument, Long> {
    interface OutlineDocument {
        String getLessonId();

        Long getId();

        String getName();

        com.bidv.coretraining.entity.DocumentType getDocumentType();

        String getFileUrl();

        String getOriginalFileName();

        Long getFileSize();

        Integer getDocumentOrder();
    }

    @org.springframework.data.jpa.repository.Query("""
            select l.id as lessonId, d.id as id, d.name as name, d.documentType as documentType,
              d.fileUrl as fileUrl, d.originalFileName as originalFileName,
              d.fileSize as fileSize, d.documentOrder as documentOrder
            from LessonDocument d join d.lesson l join l.chapter ch
            where l.course.id = :courseId
              and (:includeDraft = true or (l.status = 'PUBLISHED' and ch.status = 'PUBLISHED'))
            order by l.id, d.documentOrder, d.id
            """)
    java.util.List<OutlineDocument> findOutlineDocuments(
            @org.springframework.data.repository.query.Param("courseId") String courseId,
            @org.springframework.data.repository.query.Param("includeDraft") boolean includeDraft);

    interface IdOnly {
        Long getId();
    }

    java.util.List<IdOnly> findAllByLessonId(String lessonId);
    boolean existsByIdAndLessonId(Long id, String lessonId);

    Optional<IdOnly> findFirstByLessonIdOrderByDocumentOrderAscIdAsc(String lessonId);
}
