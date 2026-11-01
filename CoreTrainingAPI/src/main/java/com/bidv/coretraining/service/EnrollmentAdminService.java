package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;

import java.util.List;

public interface EnrollmentAdminService {
    List<EnrollmentRequestDto> getEnrollmentRequests();

    EnrollmentRequestDto reviewEnrollmentRequest(
            Long id, boolean approve, String rejectionReason, String reviewedBy);
}
