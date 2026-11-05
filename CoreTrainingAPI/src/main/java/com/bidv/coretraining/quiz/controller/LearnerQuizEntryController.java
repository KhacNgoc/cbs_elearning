package com.bidv.coretraining.quiz.controller;
import com.bidv.coretraining.quiz.dto.LearnerQuizEntryDto;
import com.bidv.coretraining.quiz.service.LearnerQuizEntryService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users/me/learning")
public class LearnerQuizEntryController {
 private final LearnerQuizEntryService service;
 public LearnerQuizEntryController(LearnerQuizEntryService service){this.service=service;}
 @GetMapping("/quiz-context")
 public LearnerQuizEntryDto.Context context(@RequestParam String courseId,@RequestParam(required=false) String lessonId){return service.context(courseId,lessonId);}
}
