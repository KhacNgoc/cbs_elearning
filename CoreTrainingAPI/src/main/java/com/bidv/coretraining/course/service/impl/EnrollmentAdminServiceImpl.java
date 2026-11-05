package com.bidv.coretraining.course.service.impl;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.enums.EnrollmentStatus;
import com.bidv.coretraining.enums.NotificationType;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.notification.service.NotificationService;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.bidv.coretraining.course.dto.EnrollmentRequestDto;
import com.bidv.coretraining.course.entity.CourseEnrollmentRequest;
import com.bidv.coretraining.course.repository.CourseEnrollmentRequestRepository;
import com.bidv.coretraining.course.service.EnrollmentAdminService;
import com.bidv.coretraining.user.repository.UserRepository;

@Service
public class EnrollmentAdminServiceImpl implements EnrollmentAdminService {
    private final AuthorizationService authorization;
    private final UserRepository userRepository;
    private final CourseEnrollmentRequestRepository enrollmentRequestRepository;
    private final NotificationService notificationService;

    public EnrollmentAdminServiceImpl(AuthorizationService authorization, UserRepository userRepository,
            CourseEnrollmentRequestRepository enrollmentRequestRepository,
            NotificationService notificationService) {
        this.authorization = authorization;
        this.userRepository = userRepository;
        this.enrollmentRequestRepository = enrollmentRequestRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRequestDto> getEnrollmentRequests() {
        authorization.permission("ENROLLMENT_MANAGE");
        return (AccessPolicy.system(authorization.current())
                ? enrollmentRequestRepository.findAllByOrderByRequestedAtDesc()
                : enrollmentRequestRepository
                        .findByUserOrganizationUnitIdInOrderByRequestedAtDesc(authorization.scopeIds()))
                .stream()
                .filter(r -> authorization.allowedScope(r.getUser().getOrganizationUnit())
                        && AccessPolicy.visible(r.getUser(), r.getCourse()))
                .map(this::toEnrollmentRequestDto)
                .toList();
    }

    @Override
    @Transactional
    public EnrollmentRequestDto reviewEnrollmentRequest(
            Long id, boolean approve, String rejectionReason, String reviewedBy) {
        CourseEnrollmentRequest request = enrollmentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment request", "id", id));
        authorization.permission("ENROLLMENT_MANAGE");
        authorization.scope(request.getUser().getOrganizationUnit());
        AccessPolicy
                .require(AccessPolicy.visible(request.getUser(), request.getCourse()));
        if (request.getStatus() != EnrollmentStatus.PENDING) {
            throw new BadRequestException("Yêu cầu đăng ký này đã được xử lý");
        }
        if (!approve && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new BadRequestException("Quản trị viên phải nêu lý do từ chối");
        }
        request.setStatus(approve ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        request.setReviewedAt(java.time.LocalDateTime.now());
        request.setReviewedBy(reviewedBy);
        request.setRejectionReason(approve ? null : rejectionReason.trim());
        if (approve) {
            request.getUser().getRegisteredCourses().add(request.getCourse());
            userRepository.save(request.getUser());
        }
        EnrollmentRequestDto result = toEnrollmentRequestDto(enrollmentRequestRepository.save(request));
        notificationService.create(
                request.getUser().getUsername(),
                approve ? NotificationType.ENROLLMENT_APPROVED : NotificationType.ENROLLMENT_REJECTED,
                approve ? "Đăng ký khóa học đã được duyệt" : "Đăng ký khóa học bị từ chối",
                approve
                        ? "Bạn đã được ghi danh vào khóa học " + request.getCourse().getName()
                        : "Khóa học " + request.getCourse().getName()
                                + " bị từ chối. Lý do: " + request.getRejectionReason(),
                approve ? "/" : null);
        return result;
    }

    private EnrollmentRequestDto toEnrollmentRequestDto(CourseEnrollmentRequest request) {
        return EnrollmentRequestDto.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .username(request.getUser().getUsername())
                .fullName(request.getUser().getFullName())
                .courseId(request.getCourse().getId())
                .courseName(request.getCourse().getName())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewedBy(request.getReviewedBy())
                .rejectionReason(request.getRejectionReason())
                .build();
    }
}
