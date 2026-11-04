package com.bidv.coretraining.controller;
import com.bidv.coretraining.dto.LearnerWorkspaceDto;

import com.bidv.coretraining.service.*;
import com.bidv.coretraining.dto.LearnerDashboardDto;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users/me/learning/dashboard")
public class LearnerDashboardController {
    private final LearnerDashboardService service;
    public LearnerDashboardController(LearnerDashboardService service) {this.service=service;}
    @GetMapping("/summary")
    public LearnerDashboardDto.Overview summary(@RequestParam(defaultValue="mine") String tab) {return service.overview(tab);}
    @GetMapping("/courses")
    public LearnerDashboardDto.Page courses(@RequestParam(defaultValue="mine") String tab,@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String domainId,@RequestParam(defaultValue="") String businessModule,
        @RequestParam(defaultValue="") String programId,@RequestParam(defaultValue="") String level,@RequestParam(defaultValue="") String source,
        @RequestParam(defaultValue="false") boolean mandatory,@RequestParam(defaultValue="deadline") String sort,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int size) {
        return service.page(new LearnerDashboardDto.Filter(tab,search,status,domainId,businessModule,programId,level,source,mandatory,sort,page,size));
    }
    @GetMapping("/courses/{id}/resume")
    public LearnerWorkspaceDto.Resume resume(@PathVariable String id) { return service.resume(id); }
    @GetMapping("/choices")
    public List<com.bidv.coretraining.dto.LearnerDashboardDto.CourseChoice> choices(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="50") int size) {return service.choices(search,size);}
}
