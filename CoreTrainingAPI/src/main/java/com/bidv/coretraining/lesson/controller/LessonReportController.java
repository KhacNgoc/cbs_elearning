package com.bidv.coretraining.lesson.controller;

import com.bidv.coretraining.lesson.dto.LessonReportDto;
import com.bidv.coretraining.lesson.dto.LessonReportReviewRequest;
import com.bidv.coretraining.lesson.service.LessonReportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.util.List;

@RestController
public class LessonReportController {
    private final LessonReportService service;

    public LessonReportController(LessonReportService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/lessons/{lessonId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LessonReportDto submit(@PathVariable String lessonId, @RequestParam String content,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return service.submit(lessonId, content, images);
    }

    @GetMapping("/api/admin/content-reports")
    public List<LessonReportDto> list() {
        return service.list();
    }

    @PutMapping("/api/admin/content-reports/{id}")
    public LessonReportDto review(@PathVariable Long id, @RequestBody LessonReportReviewRequest request) {
        return service.review(id, request.version(), request.status(), request.resolution());
    }

    @GetMapping("/api/lesson-reports/{id}")
    public LessonReportDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/api/lesson-reports/{id}/images/{index}")
    public ResponseEntity<Resource> image(@PathVariable Long id, @PathVariable int index) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(service.image(id, index)));
    }
}
