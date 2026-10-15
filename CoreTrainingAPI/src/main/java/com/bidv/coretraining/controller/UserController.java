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

    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.security.JwtTokenProvider tokens;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.security.JwtCookieService cookies;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.security.UserDetailsServiceImpl detailsService;
    private final UserService userService;
    private final LessonProgressService lessonProgressService;

    public UserController(UserService userService, LessonProgressService lessonProgressService) {
        this.userService = userService;
        this.lessonProgressService = lessonProgressService;
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<UserProfileDto> updateProfile(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody java.util.Map<String, String> fields) {
        return ResponseEntity.ok(userService.updateProfile(user.getUsername(), fields));
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

    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('LEARN')")
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
        if (newPassword.length() < 8)
            throw new com.bidv.coretraining.exception.BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
        userService.changePassword(userDetails.getUsername(), newPassword);
        var fresh = detailsService.loadUserByUsername(userDetails.getUsername());
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(fresh, null,
                fresh.getAuthorities());
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookies.create(tokens.generateToken(auth)))
                .body("Đổi mật khẩu thành công!");
    }
}
