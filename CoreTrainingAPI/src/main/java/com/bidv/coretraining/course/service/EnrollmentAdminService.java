package com.bidv.coretraining.course.service;


import com.bidv.coretraining.course.dto.EnrollmentRequestDto;
import java.util.List;

public interface EnrollmentAdminService {
    List<EnrollmentRequestDto> getEnrollmentRequests();

    EnrollmentRequestDto reviewEnrollmentRequest(
            Long id, boolean approve, String rejectionReason, String reviewedBy);
}
