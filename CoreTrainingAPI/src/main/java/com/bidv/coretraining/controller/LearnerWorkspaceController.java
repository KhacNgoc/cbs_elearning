package com.bidv.coretraining.controller;
import com.bidv.coretraining.dto.LearnerWorkspaceDto;

import com.bidv.coretraining.service.LearnerWorkspaceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class LearnerWorkspaceController {
    private final LearnerWorkspaceService service;

    public LearnerWorkspaceController(LearnerWorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/learning")
    public LearnerWorkspaceDto.Workspace workspace() {
        return service.workspace();
    }
}
