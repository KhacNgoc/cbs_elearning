package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.NotificationDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getForUser(userDetails.getUsername()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.markRead(id, userDetails.getUsername()));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllRead(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
