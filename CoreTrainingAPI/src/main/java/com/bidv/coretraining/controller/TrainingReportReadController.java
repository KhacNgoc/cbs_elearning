package com.bidv.coretraining.controller;
import com.bidv.coretraining.service.TrainingReportReadService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/admin/reports/training")
public class TrainingReportReadController {
    private final TrainingReportReadService reports;
    public TrainingReportReadController(TrainingReportReadService reports) {this.reports=reports;}
    @GetMapping("/read")
    public Map<String,Object> read(@RequestParam Map<String,String> params) {return reports.read(params);}
    @GetMapping("/attempts")
    public Map<String,Object> attempts(@RequestParam String learnerId,@RequestParam String courseId,@RequestParam(required=false) String before,@RequestParam(defaultValue="20") int size) {return reports.attempts(learnerId,courseId,before,size);}
    @GetMapping("/attempts/{attemptId}/analysis")
    public com.bidv.coretraining.dto.TrainingReportDto.AttemptAnalysis attemptAnalysis(@PathVariable long attemptId) {return reports.attemptAnalysis(attemptId);}
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    public void export(@RequestParam Map<String,String> params,jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setCharacterEncoding("UTF-8");response.setHeader("Content-Disposition","attachment; filename=training-report.csv");reports.export(params,response.getWriter());
    }
}
