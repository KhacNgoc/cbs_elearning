package com.bidv.coretraining.lesson.controller;

import com.bidv.coretraining.lesson.dto.LessonChangeDto;
import com.bidv.coretraining.lesson.dto.LessonChangeLogDto;
import com.bidv.coretraining.lesson.service.LessonChangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LessonChangeController {
    private final LessonChangeService changeService;

    public LessonChangeController(LessonChangeService changeService) {
        this.changeService = changeService;
    }

    @GetMapping("/admin/lessons/{id}/completed-learners-count")
    public LessonChangeDto.CompletedLearnersCount getCompletedLearnersCount(@PathVariable String id) {
        return changeService.countCompletedLearners(id);
    }

    @GetMapping("/lessons/{id}/latest-changelog")
    public ResponseEntity<LessonChangeLogDto> getLatestChangelog(@PathVariable String id) {
        return changeService.getLatestChangeLog(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/admin/lessons/{id}/ai-summarize-changes")
    public LessonChangeDto.AiSummary aiSummarizeChanges(@PathVariable String id,
            @RequestBody(required = false) LessonChangeDto.AiSummaryRequest request) {
        return changeService.summarizeChanges(id, request);
    }

    @PostMapping("/admin/lessons/{id}/changelog-notify")
    public LessonChangeDto.NotifyResult applyChangelogAndNotify(@PathVariable String id,
            @RequestBody LessonChangeDto.ChangeNotifyRequest request) {
        return changeService.applyChangeAndNotify(id, request);
    }
}
