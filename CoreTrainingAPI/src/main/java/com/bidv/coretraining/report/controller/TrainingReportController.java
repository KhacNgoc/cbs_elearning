package com.bidv.coretraining.report.controller;
import com.bidv.coretraining.report.dto.TrainingReportDto;
import com.bidv.coretraining.report.service.ReportCapacity;
import com.bidv.coretraining.report.service.TrainingReportService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/reports")
public class TrainingReportController {
    private final TrainingReportService service;
    private final ReportCapacity capacity;
    public TrainingReportController(TrainingReportService service, ReportCapacity capacity) { this.service=service; this.capacity=capacity; }
    @GetMapping("/training")
    public TrainingReportDto.Snapshot training() { return capacity.run(service::snapshot); }
}
