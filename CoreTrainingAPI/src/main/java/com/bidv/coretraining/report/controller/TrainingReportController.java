package com.bidv.coretraining.report.controller;
import com.bidv.coretraining.report.dto.TrainingReportDto;
import com.bidv.coretraining.report.service.TrainingReportService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/reports")
public class TrainingReportController {
    private final TrainingReportService service;
    public TrainingReportController(TrainingReportService service) { this.service=service; }
    @GetMapping("/training")
    public TrainingReportDto.Snapshot training() { return service.snapshot(); }
}
