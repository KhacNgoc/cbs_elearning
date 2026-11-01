package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.LearnerDeadlineDto.*;
import com.bidv.coretraining.service.LearnerDeadlineService;
import org.springframework.web.bind.annotation.*;
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

    @PutMapping
    public void save(@PathVariable Long userId, @RequestBody Request request) {
        service.save(userId, request);
    }
}
