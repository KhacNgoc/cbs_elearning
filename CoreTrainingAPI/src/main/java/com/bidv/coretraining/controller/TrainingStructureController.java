package com.bidv.coretraining.controller;

import com.bidv.coretraining.service.TrainingStructureService;
import com.bidv.coretraining.service.TrainingStructureService.*;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TrainingStructureController {
    private final TrainingStructureService service;

    public TrainingStructureController(TrainingStructureService service) {
        this.service = service;
    }

    @GetMapping("/admin/training/catalog")
    public Map<String, Object> catalog() {
        return service.catalog();
    }

    @PostMapping("/admin/training-domains")
    public DomainDto domain(@RequestBody DomainDto r) {
        return service.saveDomain(null, r);
    }

    @PutMapping("/admin/training-domains/{id}")
    public DomainDto domain(@PathVariable String id, @RequestBody DomainDto r) {
        return service.saveDomain(id, r);
    }

    @PostMapping("/admin/training-programs")
    public ProgramDto program(@RequestBody ProgramDto r) {
        return service.saveProgram(null, r);
    }

    @PutMapping("/admin/training-programs/{id}")
    public ProgramDto program(@PathVariable String id, @RequestBody ProgramDto r) {
        return service.saveProgram(id, r);
    }

    @GetMapping("/admin/courses")
    public List<CourseDto> courses() {
        return service.adminCourses();
    }

    @GetMapping("/admin/courses/{id}/chapters")
    public List<ChapterDto> chapters(@PathVariable String id) {
        return service.adminChapters(id);
    }

    @GetMapping("/admin/chapters/{id}")
    public ChapterDto chapter(@PathVariable String id) {
        return service.adminChapter(id);
    }

    @PostMapping("/admin/courses/{id}/chapters")
    public ChapterDto chapter(@PathVariable String id, @RequestBody ChapterRequest r) {
        return service.saveChapter(id, null, r);
    }

    @PutMapping("/admin/courses/{courseId}/chapters/{id}")
    public ChapterDto chapter(@PathVariable String courseId, @PathVariable String id, @RequestBody ChapterRequest r) {
        return service.saveChapter(courseId, id, r);
    }

    @DeleteMapping("/admin/chapters/{id}")
    public void deleteChapter(@PathVariable String id) {
        service.deleteChapter(id);
    }

    @PutMapping("/admin/courses/{id}/chapters/order")
    public void reorderChapters(@PathVariable String id, @RequestBody List<String> r) {
        service.reorderChapters(id, r);
    }

    @PutMapping("/admin/chapters/{id}/lessons/order")
    public void reorderLessons(@PathVariable String id, @RequestBody List<String> r) {
        service.reorderLessons(id, r);
    }

    @PostMapping("/admin/courses/{id}/status")
    public CourseDto status(@PathVariable String id, @RequestBody Map<String, String> r) {
        return service.status(id, r.get("status"));
    }

    @GetMapping("/courses/{id}/outline")
    public OutlineDto outline(@PathVariable String id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.outline(id, user.getUsername());
    }
}
