package com.bidv.coretraining.controller;
import com.bidv.coretraining.service.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users/me")
public class ProfileAssessmentController {
 private final ProfileAssessmentQuery query;private final AuthorizationService auth;
 public ProfileAssessmentController(ProfileAssessmentQuery query,AuthorizationService auth){this.query=query;this.auth=auth;}
 @GetMapping("/awards") public ProfileAssessmentQuery.Awards awards(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return query.awards(auth.current(),page,size);}
 @GetMapping("/assessments") public ProfileAssessmentQuery.Results results(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,@RequestParam(defaultValue="") String search){return query.results(auth.current(),page,size,search);}
 @GetMapping("/assessments/history") public ProfileAssessmentQuery.History history(@RequestParam String quizId,@RequestParam int revision,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size){return query.history(auth.current(),quizId,revision,cursor,size);}
 @GetMapping("/assessment-statistics") public java.util.Map<String,Object> stats(){return query.stats(auth.current());}
}
