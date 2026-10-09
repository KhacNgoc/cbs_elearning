package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.CourseDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, String> {
    List<CourseDocument> findByCourseIdOrderByCreatedAtDesc(String courseId);

    @EntityGraph(attributePaths = "course")
    @Query("select document from CourseDocument document where document.id = :id")
    Optional<CourseDocument> findWithCourseById(@Param("id") String id);
}
