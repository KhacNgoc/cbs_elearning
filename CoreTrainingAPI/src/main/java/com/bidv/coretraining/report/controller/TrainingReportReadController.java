package com.bidv.coretraining.report.controller;
import com.bidv.coretraining.report.service.ReportCapacity;
import com.bidv.coretraining.report.service.TrainingReportReadService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/admin/reports/training")
public class TrainingReportReadController {
    private final TrainingReportReadService reports;
    private final ReportCapacity capacity;
    public TrainingReportReadController(TrainingReportReadService reports,ReportCapacity capacity) {this.reports=reports;this.capacity=capacity;}
    // Read and export scan every learner-course pair in scope; attempts read one learner and stay unlimited.
    @GetMapping("/read")
    public Map<String,Object> read(@RequestParam Map<String,String> params) {return capacity.run(()->reports.read(params));}
    @GetMapping("/attempts")
    public Map<String,Object> attempts(@RequestParam String learnerId,@RequestParam String courseId,@RequestParam(required=false) String before,@RequestParam(defaultValue="20") int size) {return reports.attempts(learnerId,courseId,before,size);}
    @GetMapping("/attempts/{attemptId}/analysis")
    public com.bidv.coretraining.report.dto.TrainingReportDto.AttemptAnalysis attemptAnalysis(@PathVariable String attemptId) {return reports.attemptAnalysis(attemptId);}
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    public void export(@RequestParam Map<String,String> params,jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        capacity.run(()->{
            response.setContentType("text/csv;charset=UTF-8");response.setHeader("Content-Disposition","attachment; filename=training-report.csv");reports.export(params,response.getWriter());
            return null;
        });
    }
}
