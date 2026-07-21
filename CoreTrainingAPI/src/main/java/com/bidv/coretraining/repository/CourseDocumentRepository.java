package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.CourseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, String> {
    List<CourseDocument> findByTopicIdOrderByCreatedAtDesc(String topicId);
}
