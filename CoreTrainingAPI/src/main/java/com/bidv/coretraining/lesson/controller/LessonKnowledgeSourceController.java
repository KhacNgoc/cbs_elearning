package com.bidv.coretraining.lesson.controller;

import com.bidv.coretraining.document.dto.DocumentOutlineDto;
import com.bidv.coretraining.lesson.dto.LessonKnowledgeSourceDto;
import com.bidv.coretraining.lesson.service.LessonKnowledgeSourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class LessonKnowledgeSourceController {
    private final LessonKnowledgeSourceService service;

    public LessonKnowledgeSourceController(LessonKnowledgeSourceService service) {
        this.service = service;
    }

    @GetMapping("/lessons/{lessonId}/knowledge-sources")
    public List<LessonKnowledgeSourceDto> list(@PathVariable String lessonId) {
        return service.listAuthorized(lessonId);
    }

    @PutMapping("/lessons/{lessonId}/knowledge-sources")
    public List<LessonKnowledgeSourceDto> save(@PathVariable String lessonId,
            @RequestBody @Valid List<@Valid LessonKnowledgeSourceDto> sources) {
        return service.saveAuthorized(lessonId, sources);
    }

    @GetMapping("/courses/{courseId}/documents/{documentId}/outline")
    public DocumentOutlineDto outline(@PathVariable String courseId, @PathVariable String documentId) {
        return service.outlineAuthorized(courseId, documentId);
    }
}
