package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.dto.UserProfileDto;
import com.bidv.coretraining.dto.LessonProgressDto;
import com.bidv.coretraining.dto.LessonProgressRequest;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.LessonProgressService;
import com.bidv.coretraining.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final LessonProgressService lessonProgressService;

    public UserController(UserService userService, LessonProgressService lessonProgressService) {
        this.userService = userService;
        this.lessonProgressService = lessonProgressService;
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

    @DeleteMapping("/me/lessons/{lessonId}/completion")
    public ResponseEntity<UserProgressDto> resetLesson(
            @PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.resetLesson(userDetails.getUsername(), lessonId));
    }

    @PostMapping("/me/lessons/{lessonId}/progress")
    public ResponseEntity<LessonProgressDto> saveLessonProgress(
            @PathVariable String lessonId,
            @jakarta.validation.Valid @RequestBody LessonProgressRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(lessonProgressService.saveProgress(
                userDetails.getUsername(), lessonId, request));
    }

    @PostMapping("/me/lessons/{lessonId}/content-complete")
    public ResponseEntity<UserProgressDto> completeContent(@PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        lessonProgressService.completeContent(userDetails.getUsername(), lessonId);
        return ResponseEntity.ok(userService.getUserProgress(userDetails.getUsername()));
    }

    @PostMapping("/me/register-course/{courseId}")
    public ResponseEntity<UserProgressDto> registerCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProgressDto response = userService.registerCourse(userDetails.getUsername(), courseId);
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
