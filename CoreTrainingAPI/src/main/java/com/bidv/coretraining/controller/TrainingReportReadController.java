package com.bidv.coretraining.controller;

import com.bidv.coretraining.service.AuthorizationService;
import com.bidv.coretraining.service.TrainingReportQuery;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/admin/reports/training")
@Transactional(readOnly=true)
public class TrainingReportReadController {
    private final AuthorizationService auth;
    private final TrainingReportQuery query;
    public TrainingReportReadController(AuthorizationService auth,TrainingReportQuery query){this.auth=auth;this.query=query;}
    private TrainingReportQuery.Filter filter(Map<String,String> p){
        try{return new TrainingReportQuery.Filter(p.get("from"),p.get("to"),p.get("unit"),p.get("group"),p.get("program"),p.get("course"),p.get("status"),p.getOrDefault("view","completion"),p.get("action"),p.get("search"),Integer.parseInt(p.getOrDefault("page","0")),Integer.parseInt(p.getOrDefault("size","20")),p.get("dimension"),p.get("key"));}
        catch(NumberFormatException e){throw new com.bidv.coretraining.exception.BadRequestException("Tham số phân trang không hợp lệ");}
    }
    @GetMapping("/read")
    public Map<String,Object> read(@RequestParam Map<String,String> params){auth.requireOperation("overview","read");return query.read(auth.current(),filter(params),!"false".equals(params.get("metadata")));}
    @GetMapping("/attempts")
    public Map<String,Object> attempts(@RequestParam String learnerId,@RequestParam String courseId,@RequestParam(required=false) String before,@RequestParam(defaultValue="20") int size){auth.requireOperation("overview","read");return query.attempts(auth.current(),learnerId,courseId,before,size);}
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    public void export(@RequestParam Map<String,String> params,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException {
        auth.requireOperation("overview","read");response.setCharacterEncoding("UTF-8");response.setHeader("Content-Disposition","attachment; filename=training-report.csv");query.export(auth.current(),filter(params),response.getWriter());
    }
}
