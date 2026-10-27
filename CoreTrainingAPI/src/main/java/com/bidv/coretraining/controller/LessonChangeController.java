package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.LessonChangeLogDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.service.AuthorizationService;
import com.bidv.coretraining.service.LessonChangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LessonChangeController {
    private final LessonChangeService changeService;
    private final LessonRepository lessonRepository;
    private final AuthorizationService authorizationService;

    public LessonChangeController(
            LessonChangeService changeService,
            LessonRepository lessonRepository,
            AuthorizationService authorizationService) {
        this.changeService = changeService;
        this.lessonRepository = lessonRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/admin/lessons/{id}/completed-learners-count")
    public ResponseEntity<Map<String, Object>> getCompletedLearnersCount(@PathVariable String id) {
        int count = changeService.countCompletedLearners(id);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/lessons/{id}/latest-changelog")
    public ResponseEntity<?> getLatestChangelog(@PathVariable String id) {
        return changeService.getLatestChangeLog(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    public record AiSummaryRequest(String oldContent, String newContent) {}

    @PostMapping("/admin/lessons/{id}/ai-summarize-changes")
    public ResponseEntity<Map<String, Object>> aiSummarizeChanges(
            @PathVariable String id,
            @RequestBody(required = false) AiSummaryRequest request) {
        Lesson lesson = lessonRepository.findById(id).orElse(null);
        String title = lesson != null ? lesson.getTitle() : "Bài học";
        String oldContent = (request != null && request.oldContent() != null) ? request.oldContent() : "";
        String newContent = (request != null && request.newContent() != null) ? request.newContent() : "";

        String summary = changeService.generateAiSummary(title, oldContent, newContent);
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    public record ChangeNotifyRequest(String changeSummary, Boolean notifyLearners) {}

    @PostMapping("/admin/lessons/{id}/changelog-notify")
    public ResponseEntity<Map<String, Object>> applyChangelogAndNotify(
            @PathVariable String id,
            @RequestBody ChangeNotifyRequest request) {
        String username = authorizationService.current() != null ? authorizationService.current().getUsername() : "system";
        boolean notify = request.notifyLearners() != null && request.notifyLearners();
        int notified = changeService.applyLessonChangeAndNotify(id, request.changeSummary(), username, notify);
        return ResponseEntity.ok(Map.of("notifiedCount", notified));
    }
}
