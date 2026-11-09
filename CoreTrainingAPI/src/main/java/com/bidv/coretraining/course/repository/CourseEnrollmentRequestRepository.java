package com.bidv.coretraining.course.repository;

import com.bidv.coretraining.course.entity.CourseEnrollmentRequest;
import com.bidv.coretraining.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseEnrollmentRequestRepository extends JpaRepository<CourseEnrollmentRequest, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from CourseEnrollmentRequest r where r.id = :id")
    java.util.Optional<CourseEnrollmentRequest> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByUserIdAndCourseIdAndStatus(Long userId, String courseId, EnrollmentStatus status);

    List<CourseEnrollmentRequest> findByUserIdAndStatus(Long userId, EnrollmentStatus status);

    List<CourseEnrollmentRequest> findAllByOrderByRequestedAtDesc();

    List<CourseEnrollmentRequest> findByUserOrganizationUnitIdInOrderByRequestedAtDesc(
            java.util.Collection<String> unitIds);
}
