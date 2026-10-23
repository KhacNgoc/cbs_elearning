package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.CommentDto;
import com.bidv.coretraining.dto.CommentRequest;
import com.bidv.coretraining.dto.CommentReplyRequest;
import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.CourseDto;
import com.bidv.coretraining.dto.VideoNoteDto;
import com.bidv.coretraining.dto.VideoNoteRequest;
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

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<CourseDto> getCourseById(@PathVariable String id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<LessonDto> getLessonById(@PathVariable String id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(courseService.getLessonById(id));
    }

    @GetMapping("/lessons/{id}/comments")
    public com.bidv.coretraining.dto.CommentPageDto comments(@PathVariable String id,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return courseService.getComments(id, page, size);
    }

    @PostMapping("/lessons/{id}/comments")
    public ResponseEntity<CommentDto> addCommentToLesson(
            @PathVariable String id,
            @Valid @RequestBody CommentRequest commentRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CommentDto response = courseService.addCommentToLesson(id, commentRequest, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/lessons/{lessonId}/comments/{commentId}/replies")
    public ResponseEntity<CommentDto> replyToComment(
            @PathVariable String lessonId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentReplyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                courseService.replyToComment(lessonId, commentId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/lessons/{lessonId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String lessonId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        courseService.deleteComment(
                lessonId, commentId, userDetails.getUsername(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons/{id}/notes")
    public ResponseEntity<List<VideoNoteDto>> getVideoNotes(
            @PathVariable String id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(courseService.getVideoNotes(id, userDetails.getUsername()));
    }

    @PostMapping("/lessons/{id}/notes")
    public ResponseEntity<VideoNoteDto> createVideoNote(
            @PathVariable String id,
            @Valid @RequestBody VideoNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(courseService.createVideoNote(id, request, userDetails.getUsername()));
    }

    @PutMapping("/lessons/{lessonId}/notes/{noteId}")
    public ResponseEntity<VideoNoteDto> updateVideoNote(
            @PathVariable String lessonId,
            @PathVariable Long noteId,
            @Valid @RequestBody VideoNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(courseService.updateVideoNote(
                lessonId, noteId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/lessons/{lessonId}/notes/{noteId}")
    public ResponseEntity<Void> deleteVideoNote(
            @PathVariable String lessonId,
            @PathVariable Long noteId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        courseService.deleteVideoNote(lessonId, noteId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
