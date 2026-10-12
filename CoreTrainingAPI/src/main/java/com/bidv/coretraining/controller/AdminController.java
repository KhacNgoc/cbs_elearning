package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.bidv.coretraining.service.AdminService;
import com.bidv.coretraining.service.DocumentStorageService;
import com.bidv.coretraining.service.VideoStorageService;
import com.bidv.coretraining.service.QuizGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.AuthorizationService authorization;

    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.AssetAccessService assets;
    private final AdminService adminService;
    private final DocumentStorageService documentStorageService;
    private final VideoStorageService videoStorageService;
    private final QuizGenerationService quizGenerationService;

    public AdminController(AdminService adminService, DocumentStorageService documentStorageService,
                           VideoStorageService videoStorageService,
                           QuizGenerationService quizGenerationService) {
        this.adminService = adminService;
        this.documentStorageService = documentStorageService;
        this.videoStorageService = videoStorageService;
        this.quizGenerationService = quizGenerationService;
    }

    // Courses CRUD
    @PostMapping("/courses")
    public ResponseEntity<CourseDto> createCourse(@RequestBody CourseDto courseDto) {
        return ResponseEntity.ok(adminService.createCourse(courseDto));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<CourseDto> updateCourse(@PathVariable String id, @RequestBody CourseDto courseDto) {
        return ResponseEntity.ok(adminService.updateCourse(id, courseDto));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable String id) {
        adminService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    // Lessons CRUD
    @PostMapping(value = "/documents/upload", consumes = "multipart/form-data")
    public ResponseEntity<LessonDocumentDto> uploadDocument(@RequestPart("file") MultipartFile file) {
        LessonDocumentDto document=documentStorageService.store(file);assets.record(document.getFileUrl());return ResponseEntity.ok(document);
    }

    @PostMapping(value = "/videos/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadVideo(@RequestPart("file") MultipartFile file) {
        String path=videoStorageService.store(file);assets.record(path);return ResponseEntity.ok(Map.of("videoUrl",path));
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

    @PutMapping("/courses/{courseId}/lessons/order")
    public ResponseEntity<List<LessonDto>> reorderLessons(
            @PathVariable String courseId,
            @RequestBody List<String> lessonIds) {
        return ResponseEntity.ok(adminService.reorderLessons(courseId, lessonIds));
    }

    // Questions CRUD
    @GetMapping("/quizzes/questions")
    public ResponseEntity<List<QuestionDto>> getQuizQuestions(
            @RequestParam String courseId,
            @RequestParam(required = false) String lessonId) {
        return ResponseEntity.ok(adminService.getQuizQuestions(courseId, lessonId));
    }

    @PostMapping("/quizzes/generate")
    public ResponseEntity<QuizGenerationResponseDto> generateQuiz(
            @Valid @RequestBody QuizGenerationRequestDto request) {
        authorization.edit(request.getCourseId());
        return ResponseEntity.ok(quizGenerationService.generate(request));
    }

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
    public ResponseEntity<Void> deleteQuiz(@RequestParam String courseId,
                                           @RequestParam(required = false) String lessonId) {
        adminService.deleteQuiz(courseId, lessonId);
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
}
