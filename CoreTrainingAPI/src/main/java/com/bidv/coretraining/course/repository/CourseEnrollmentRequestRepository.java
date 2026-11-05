package com.bidv.coretraining.course.repository;

import com.bidv.coretraining.course.entity.CourseEnrollmentRequest;
import com.bidv.coretraining.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseEnrollmentRequestRepository extends JpaRepository<CourseEnrollmentRequest, Long> {
    boolean existsByUserIdAndCourseIdAndStatus(Long userId, String courseId, EnrollmentStatus status);

    List<CourseEnrollmentRequest> findByUserIdAndStatus(Long userId, EnrollmentStatus status);

    List<CourseEnrollmentRequest> findAllByOrderByRequestedAtDesc();

    List<CourseEnrollmentRequest> findByUserOrganizationUnitIdInOrderByRequestedAtDesc(
            java.util.Collection<String> unitIds);
}
