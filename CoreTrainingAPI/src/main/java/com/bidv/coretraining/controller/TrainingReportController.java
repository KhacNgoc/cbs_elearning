package com.bidv.coretraining.controller;
import com.bidv.coretraining.service.TrainingReportService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/reports")
public class TrainingReportController {
    private final TrainingReportService service;
    public TrainingReportController(TrainingReportService service) { this.service=service; }
    @GetMapping("/training")
    public TrainingReportService.Snapshot training() { return service.snapshot(); }
}
