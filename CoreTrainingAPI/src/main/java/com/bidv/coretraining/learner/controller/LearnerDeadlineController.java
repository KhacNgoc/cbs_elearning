package com.bidv.coretraining.learner.controller;

import com.bidv.coretraining.learner.dto.LearnerDeadlineDto.*;
import com.bidv.coretraining.learner.service.LearnerDeadlineService;
import org.springframework.web.bind.annotation.*;
import com.bidv.coretraining.training.dto.PagedResult;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users/{userId}/course-deadlines")
public class LearnerDeadlineController {
    private final LearnerDeadlineService service;

    public LearnerDeadlineController(LearnerDeadlineService service) {
        this.service = service;
    }

    @GetMapping
    public List<Deadline> list(@PathVariable Long userId) {
        return service.list(userId);
    }

    /** One page of {@link #list}, filtered by course name, code or id (case-insensitive). Page is 0-based. */
    @GetMapping("/paged")
    public PagedResult<Deadline> page(@PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return service.page(userId, search, page, size);
    }

    @PutMapping
    public void save(@PathVariable Long userId, @RequestBody Request request) {
        service.save(userId, request);
    }
}
