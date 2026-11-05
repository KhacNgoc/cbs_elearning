package com.bidv.coretraining.course.controller;

import com.bidv.coretraining.course.service.CourseImageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.bidv.coretraining.document.dto.UploadResultDto;

@RestController
public class CourseImageController {
    private final CourseImageService images;

    public CourseImageController(CourseImageService images) {
        this.images = images;
    }

    @PostMapping(value = "/api/admin/courses/images", consumes = "multipart/form-data")
    public UploadResultDto.Image upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String courseId, @RequestParam(required = false) String programId) {
        return new UploadResultDto.Image(images.upload(file, courseId, programId));
    }

    @GetMapping("/api/course-images/{name}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String name) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache().cachePrivate()).body(new FileSystemResource(images.read(name)));
    }
}
