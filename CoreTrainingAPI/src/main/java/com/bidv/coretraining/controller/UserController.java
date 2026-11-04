package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.dto.UserProfileDto;
import com.bidv.coretraining.dto.LessonProgressDto;
import com.bidv.coretraining.dto.LessonProgressRequest;
import com.bidv.coretraining.dto.ChangePasswordRequest;
import com.bidv.coretraining.dto.UpdateProfileRequest;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.security.JwtCookieService;
import com.bidv.coretraining.service.AuthService;
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
    private final AuthService authService;
    private final JwtCookieService cookies;

    public UserController(UserService userService, LessonProgressService lessonProgressService,
            AuthService authService, JwtCookieService cookies) {
        this.userService = userService;
        this.lessonProgressService = lessonProgressService;
        this.authService = authService;
        this.cookies = cookies;
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<UserProfileDto> updateProfile(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user.getUsername(), request));
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

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('LEARN')")
    @PostMapping("/me/complete-lesson/{lessonId}")
    public ResponseEntity<UserProgressDto> completeLesson(
            @PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProgressDto response = userService.completeLesson(userDetails.getUsername(), lessonId);
        return ResponseEntity.ok(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('LEARN')")
    @DeleteMapping("/me/lessons/{lessonId}/completion")
    public ResponseEntity<UserProgressDto> resetLesson(
            @PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.resetLesson(userDetails.getUsername(), lessonId));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('LEARN')")
    @PostMapping("/me/lessons/{lessonId}/progress")
    public ResponseEntity<LessonProgressDto> saveLessonProgress(
            @PathVariable String lessonId,
            @jakarta.validation.Valid @RequestBody LessonProgressRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(lessonProgressService.saveProgress(
                userDetails.getUsername(), lessonId, request));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('LEARN')")
    @PostMapping("/me/lessons/{lessonId}/content-complete")
    public ResponseEntity<UserProgressDto> completeContent(@PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        lessonProgressService.completeContent(userDetails.getUsername(), lessonId);
        return ResponseEntity.ok(userService.getUserProgress(userDetails.getUsername()));
    }

    // The service checks LEARN and returns a specific, user-facing reason when it is missing.
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @PostMapping("/me/register-course/{courseId}")
    public ResponseEntity<UserProgressDto> registerCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProgressDto response = userService.registerCourse(userDetails.getUsername(), courseId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.changePassword(userDetails.getUsername(), request.newPassword());
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE,
                        cookies.create(authService.reissueToken(userDetails.getUsername())))
                .body("Đổi mật khẩu thành công!");
    }
}
