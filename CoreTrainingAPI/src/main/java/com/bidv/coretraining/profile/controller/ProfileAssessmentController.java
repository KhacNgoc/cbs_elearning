package com.bidv.coretraining.profile.controller;
import com.bidv.coretraining.profile.dto.ProfileAssessmentDto;
import com.bidv.coretraining.profile.service.ProfileAssessmentService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users/me")
public class ProfileAssessmentController {
 private final ProfileAssessmentService service;
 public ProfileAssessmentController(ProfileAssessmentService service){this.service=service;}
 @GetMapping("/awards") public ProfileAssessmentDto.Awards awards(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.awards(page,size);}
 @GetMapping("/assessments") public ProfileAssessmentDto.Results results(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,@RequestParam(defaultValue="") String search){return service.results(page,size,search);}
 @GetMapping("/assessments/history") public ProfileAssessmentDto.History history(@RequestParam String quizId,@RequestParam int revision,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size){return service.history(quizId,revision,cursor,size);}
 @GetMapping("/assessment-statistics") public ProfileAssessmentDto.Stats stats(){return service.stats();}
}
