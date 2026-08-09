package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.bidv.coretraining.service.AdminService;
import com.bidv.coretraining.service.DocumentStorageService;
import com.bidv.coretraining.service.VideoStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final DocumentStorageService documentStorageService;
    private final VideoStorageService videoStorageService;

    public AdminController(AdminService adminService, DocumentStorageService documentStorageService,
                           VideoStorageService videoStorageService) {
        this.adminService = adminService;
        this.documentStorageService = documentStorageService;
        this.videoStorageService = videoStorageService;
    }

    // Topics CRUD
    @PostMapping("/topics")
    public ResponseEntity<TopicDto> createTopic(@RequestBody TopicDto topicDto) {
        return ResponseEntity.ok(adminService.createTopic(topicDto));
    }

    @PutMapping("/topics/{id}")
    public ResponseEntity<TopicDto> updateTopic(@PathVariable String id, @RequestBody TopicDto topicDto) {
        return ResponseEntity.ok(adminService.updateTopic(id, topicDto));
    }

    @DeleteMapping("/topics/{id}")
    public ResponseEntity<Void> deleteTopic(@PathVariable String id) {
        adminService.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }

    // Lessons CRUD
    @PostMapping(value = "/documents/upload", consumes = "multipart/form-data")
    public ResponseEntity<LessonDocumentDto> uploadDocument(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(documentStorageService.store(file));
    }

    @PostMapping(value = "/videos/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadVideo(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("videoUrl", videoStorageService.store(file)));
    }

    @PostMapping("/lessons")
    public ResponseEntity<LessonDto> createLesson(@RequestBody LessonDto lessonDto) {
        return ResponseEntity.ok(adminService.createLesson(lessonDto));
    }

    @PutMapping("/lessons/{id}")
    public ResponseEntity<LessonDto> updateLesson(@PathVariable String id, @RequestBody LessonDto lessonDto) {
        return ResponseEntity.ok(adminService.updateLesson(id, lessonDto));
    }

    @DeleteMapping("/lessons/{id}")
    public ResponseEntity<Void> deleteLesson(@PathVariable String id) {
        adminService.deleteLesson(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/topics/{topicId}/lessons/order")
    public ResponseEntity<List<LessonDto>> reorderLessons(
            @PathVariable String topicId,
            @RequestBody List<String> lessonIds) {
        return ResponseEntity.ok(adminService.reorderLessons(topicId, lessonIds));
    }

    // Questions CRUD
    @PostMapping("/questions")
    public ResponseEntity<QuestionDto> createQuestion(@RequestBody QuestionDto questionDto) {
        return ResponseEntity.ok(adminService.createQuestion(questionDto));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<QuestionDto> updateQuestion(@PathVariable Long id, @RequestBody QuestionDto questionDto) {
        return ResponseEntity.ok(adminService.updateQuestion(id, questionDto));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        adminService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/quizzes/settings")
    public ResponseEntity<QuizSettingsDto> updateQuizSettings(@RequestBody QuizSettingsDto settings) {
        return ResponseEntity.ok(adminService.updateQuizSettings(settings));
    }

    @DeleteMapping("/quizzes")
    public ResponseEntity<Void> deleteQuiz(@RequestParam String topicId,
                                           @RequestParam(required = false) String lessonId) {
        adminService.deleteQuiz(topicId, lessonId);
        return ResponseEntity.noContent().build();
    }

    // User CRUD endpoints
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        return ResponseEntity.ok(adminService.createUser(userDto));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody UserDto userDto) {
        return ResponseEntity.ok(adminService.updateUser(id, userDto));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/enrollment-requests")
    public ResponseEntity<List<EnrollmentRequestDto>> getEnrollmentRequests() {
        return ResponseEntity.ok(adminService.getEnrollmentRequests());
    }

    @PutMapping("/enrollment-requests/{id}")
    public ResponseEntity<EnrollmentRequestDto> reviewEnrollmentRequest(
            @PathVariable Long id,
            @RequestBody ReviewEnrollmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                adminService.reviewEnrollmentRequest(
                        id, request.isApprove(), request.getRejectionReason(),
                        userDetails.getUsername())
        );
    }

    @PostMapping("/users/upload")
    public ResponseEntity<String> uploadUsersExcel(@RequestParam("file") MultipartFile file) {
        adminService.importUsersFromExcel(file);
        return ResponseEntity.ok("Import thành công dữ liệu học viên!");
    }

    @GetMapping("/users/template")
    public ResponseEntity<byte[]> downloadUsersTemplate() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=user_import_template.xlsx")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(adminService.generateExcelTemplate());
    }

    @GetMapping("/groups")
    public ResponseEntity<List<com.bidv.coretraining.entity.UserGroup>> getAllUserGroups() {
        return ResponseEntity.ok(adminService.getAllUserGroups());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDto> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }
}
