package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from CourseDocument document where document.id = :id")
    Optional<CourseDocument> findForDeletionById(@Param("id") String id);

    List<CourseDocument> findByCourseIdOrderByCreatedAtDesc(String courseId);

    @Query("SELECT d FROM CourseDocument d WHERE d.course.id = :courseId " +
           "AND (:search IS NULL OR :search = '' OR d.fileName ILIKE CONCAT('%', :search, '%')) " +
           "AND (:filterByStatus = false OR d.status IN :statuses) " +
           "ORDER BY d.createdAt DESC")
    Page<CourseDocument> findByCourseIdWithFilter(
            @Param("courseId") String courseId,
            @Param("search") String search,
            @Param("filterByStatus") boolean filterByStatus,
            @Param("statuses") Collection<DocumentStatus> statuses,
            Pageable pageable);

    @Query("SELECT d.status, COUNT(d) FROM CourseDocument d WHERE d.course.id = :courseId GROUP BY d.status")
    List<Object[]> countStatusByCourseId(@Param("courseId") String courseId);

    @EntityGraph(attributePaths = "course")
    @Query("select document from CourseDocument document where document.id = :id")
    Optional<CourseDocument> findWithCourseById(@Param("id") String id);
}
