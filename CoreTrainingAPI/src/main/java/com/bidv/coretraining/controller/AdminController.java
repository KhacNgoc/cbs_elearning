package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.dto.TopicDto;
import com.bidv.coretraining.dto.UserDto;
import com.bidv.coretraining.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
}
