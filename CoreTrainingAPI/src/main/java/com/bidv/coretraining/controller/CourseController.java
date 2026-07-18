package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.CommentDto;
import com.bidv.coretraining.dto.CommentRequest;
import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.TopicDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<TopicDto>> getAllTopics() {
        return ResponseEntity.ok(courseService.getAllTopics());
    }

    @GetMapping("/topics/{id}")
    public ResponseEntity<TopicDto> getTopicById(@PathVariable String id) {
        return ResponseEntity.ok(courseService.getTopicById(id));
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<LessonDto> getLessonById(@PathVariable String id) {
        return ResponseEntity.ok(courseService.getLessonById(id));
    }

    @PostMapping("/lessons/{id}/comments")
    public ResponseEntity<CommentDto> addCommentToLesson(
            @PathVariable String id,
            @Valid @RequestBody CommentRequest commentRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CommentDto response = courseService.addCommentToLesson(id, commentRequest, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
