package com.bidv.coretraining.course.dto;

import lombok.Data;

@Data
public class ReviewEnrollmentRequest {
    private boolean approve;
    private String rejectionReason;
}
