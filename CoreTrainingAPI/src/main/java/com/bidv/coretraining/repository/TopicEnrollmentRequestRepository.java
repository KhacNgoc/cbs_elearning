package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.EnrollmentStatus;
import com.bidv.coretraining.entity.TopicEnrollmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicEnrollmentRequestRepository extends JpaRepository<TopicEnrollmentRequest, Long> {
    Optional<TopicEnrollmentRequest> findByUserIdAndTopicId(Long userId, String topicId);
    List<TopicEnrollmentRequest> findByUserIdAndStatus(Long userId, EnrollmentStatus status);
    List<TopicEnrollmentRequest> findAllByOrderByRequestedAtDesc();
}
