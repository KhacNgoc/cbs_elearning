package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.dto.UserProfileDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me/profile")
    public ResponseEntity<UserProfileDto> getUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getUsername()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProgressDto> getUserProgress(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProgressDto response = userService.getUserProgress(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/complete-lesson/{lessonId}")
    public ResponseEntity<UserProgressDto> completeLesson(
            @PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProgressDto response = userService.completeLesson(userDetails.getUsername(), lessonId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/register-topic/{topicId}")
    public ResponseEntity<UserProgressDto> registerTopic(
            @PathVariable String topicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProgressDto response = userService.registerTopic(userDetails.getUsername(), topicId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody java.util.Map<String, String> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String newPassword = request.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new com.bidv.coretraining.exception.BadRequestException("Mật khẩu mới không được để trống!");
        }
        userService.changePassword(userDetails.getUsername(), newPassword);
        return ResponseEntity.ok("Đổi mật khẩu thành công!");
    }
}
