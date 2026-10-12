package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.service.LessonKnowledgeSourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class LessonKnowledgeSourceController {
    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.AuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.repository.LessonRepository lessons;
    private final LessonKnowledgeSourceService service;
    public LessonKnowledgeSourceController(LessonKnowledgeSourceService service) { this.service = service; }

    @GetMapping("/lessons/{lessonId}/knowledge-sources")
    public List<LessonKnowledgeSourceDto> list(@PathVariable String lessonId) { authorization.edit(lessons.findById(lessonId).orElseThrow().getCourse()); return service.list(lessonId); }

    @PutMapping("/lessons/{lessonId}/knowledge-sources")
    public List<LessonKnowledgeSourceDto> save(@PathVariable String lessonId,
            @RequestBody @Valid List<@Valid LessonKnowledgeSourceDto> sources) {
        authorization.edit(lessons.findById(lessonId).orElseThrow().getCourse());
        return service.save(lessonId, sources);
    }

    @GetMapping("/courses/{courseId}/documents/{documentId}/outline")
    public DocumentOutlineDto outline(@PathVariable String courseId, @PathVariable String documentId) {
        authorization.edit(courseId);
        return service.outline(courseId, documentId);
    }
}
