package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.EnrollmentStatus;
import com.bidv.coretraining.entity.TopicEnrollmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicEnrollmentRequestRepository extends JpaRepository<TopicEnrollmentRequest, Long> {
    boolean existsByUserIdAndTopicIdAndStatus(Long userId, String topicId, EnrollmentStatus status);
    List<TopicEnrollmentRequest> findByUserIdAndStatus(Long userId, EnrollmentStatus status);
    List<TopicEnrollmentRequest> findAllByOrderByRequestedAtDesc();
}
