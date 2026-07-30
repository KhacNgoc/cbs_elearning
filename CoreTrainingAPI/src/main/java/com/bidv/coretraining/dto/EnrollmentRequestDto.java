package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.EnrollmentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentRequestDto {
    private Long id;
    private Long userId;
    private String username;
    private String fullName;
    private String topicId;
    private String topicName;
    private EnrollmentStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private String rejectionReason;
}
