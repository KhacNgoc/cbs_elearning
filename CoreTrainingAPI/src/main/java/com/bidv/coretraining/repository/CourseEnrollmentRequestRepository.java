package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.EnrollmentStatus;
import com.bidv.coretraining.entity.CourseEnrollmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseEnrollmentRequestRepository extends JpaRepository<CourseEnrollmentRequest, Long> {
    boolean existsByUserIdAndCourseIdAndStatus(Long userId, String courseId, EnrollmentStatus status);
    List<CourseEnrollmentRequest> findByUserIdAndStatus(Long userId, EnrollmentStatus status);
    List<CourseEnrollmentRequest> findAllByOrderByRequestedAtDesc();
    List<CourseEnrollmentRequest> findByUserOrganizationUnitIdInOrderByRequestedAtDesc(java.util.Collection<String> unitIds);
}
