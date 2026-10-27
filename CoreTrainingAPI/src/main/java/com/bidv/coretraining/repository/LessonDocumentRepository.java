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

        com.bidv.coretraining.entity.DocumentPurpose getPurpose();
    }

    @org.springframework.data.jpa.repository.Query("""
            select l.id as lessonId, d.id as id, d.name as name, d.documentType as documentType,
              d.fileUrl as fileUrl, d.originalFileName as originalFileName,
              d.fileSize as fileSize, d.documentOrder as documentOrder, d.purpose as purpose
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

    interface SlideId {
        String getLessonId();
        Long getId();
    }

    @org.springframework.data.jpa.repository.Query("""
            select d.lesson.id as lessonId, d.id as id from LessonDocument d
            where d.lesson.id in :lessonIds and d.purpose = com.bidv.coretraining.entity.DocumentPurpose.SLIDE
            """)
    java.util.List<SlideId> findSlideIds(
            @org.springframework.data.repository.query.Param("lessonIds") java.util.Collection<String> lessonIds);

    java.util.List<IdOnly> findAllByLessonId(String lessonId);
    java.util.List<IdOnly> findAllByLessonIdAndPurpose(String lessonId,
            com.bidv.coretraining.entity.DocumentPurpose purpose);
    boolean existsByIdAndLessonId(Long id, String lessonId);

    Optional<IdOnly> findFirstByLessonIdOrderByDocumentOrderAscIdAsc(String lessonId);

    Optional<IdOnly> findFirstByLessonIdAndPurposeOrderByDocumentOrderAscIdAsc(
            String lessonId, com.bidv.coretraining.entity.DocumentPurpose purpose);

    boolean existsByIdAndLessonIdAndPurpose(Long id, String lessonId,
            com.bidv.coretraining.entity.DocumentPurpose purpose);
}
