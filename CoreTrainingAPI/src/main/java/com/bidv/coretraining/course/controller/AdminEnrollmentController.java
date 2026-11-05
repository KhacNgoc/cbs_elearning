package com.bidv.coretraining.course.controller;

import com.bidv.coretraining.course.dto.EnrollmentRequestDto;
import com.bidv.coretraining.course.dto.ReviewEnrollmentRequest;
import com.bidv.coretraining.course.service.EnrollmentAdminService;
import com.bidv.coretraining.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminEnrollmentController {
    private final EnrollmentAdminService service;

    public AdminEnrollmentController(EnrollmentAdminService service) {
        this.service = service;
    }

    @GetMapping("/enrollment-requests")
    public ResponseEntity<List<EnrollmentRequestDto>> getEnrollmentRequests() {
        return ResponseEntity.ok(service.getEnrollmentRequests());
    }

    @PutMapping("/enrollment-requests/{id}")
    public ResponseEntity<EnrollmentRequestDto> reviewEnrollmentRequest(@PathVariable Long id,
            @RequestBody ReviewEnrollmentRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(service.reviewEnrollmentRequest(id, request.isApprove(),
                request.getRejectionReason(), userDetails.getUsername()));
    }
}
