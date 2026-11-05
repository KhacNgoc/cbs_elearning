package com.bidv.coretraining.course.controller;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.course.service.CourseContentAdminService;
import com.bidv.coretraining.document.dto.LessonDocumentDto;
import com.bidv.coretraining.document.dto.UploadResultDto;
import com.bidv.coretraining.lesson.dto.LessonDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminCourseContentController {
    private final CourseContentAdminService service;

    public AdminCourseContentController(CourseContentAdminService service) {
        this.service = service;
    }

    @PostMapping("/courses")
    public ResponseEntity<CourseDto> createCourse(@RequestBody CourseDto courseDto) {
        return ResponseEntity.ok(service.createCourse(courseDto));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<CourseDto> updateCourse(@PathVariable String id, @RequestBody CourseDto courseDto) {
        return ResponseEntity.ok(service.updateCourse(id, courseDto));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable String id) {
        service.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/documents/upload", consumes = "multipart/form-data")
    public ResponseEntity<LessonDocumentDto> uploadDocument(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.uploadDocument(file));
    }

    @PostMapping(value = "/videos/upload", consumes = "multipart/form-data")
    public ResponseEntity<UploadResultDto.Video> uploadVideo(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.uploadVideo(file));
    }

    @PostMapping("/lessons")
    public ResponseEntity<LessonDto> createLesson(@RequestBody LessonDto lessonDto) {
        return ResponseEntity.ok(service.createLesson(lessonDto));
    }

    @PutMapping("/lessons/{id}")
    public ResponseEntity<LessonDto> updateLesson(@PathVariable String id, @RequestBody LessonDto lessonDto) {
        return ResponseEntity.ok(service.updateLesson(id, lessonDto));
    }

    @DeleteMapping("/lessons/{id}")
    public ResponseEntity<Void> deleteLesson(@PathVariable String id) {
        service.deleteLesson(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/courses/{courseId}/lessons/order")
    public ResponseEntity<List<LessonDto>> reorderLessons(@PathVariable String courseId,
            @RequestBody List<String> lessonIds) {
        return ResponseEntity.ok(service.reorderLessons(courseId, lessonIds));
    }
}
