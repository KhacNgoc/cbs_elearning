package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.dto.QuizContextDto;
import com.bidv.coretraining.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.dto.QuizGenerationResponseDto;
import com.bidv.coretraining.dto.QuizSettingsDto;
import com.bidv.coretraining.dto.QuizSummaryPageDto;
import com.bidv.coretraining.service.QuizAdminService;
import com.bidv.coretraining.service.QuizContextService;
import com.bidv.coretraining.service.QuizSummaryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminQuizController {
    private final QuizAdminService service;
    private final QuizContextService quizContext;
    private final QuizSummaryService quizSummaries;

    public AdminQuizController(QuizAdminService service, QuizContextService quizContext,
            QuizSummaryService quizSummaries) {
        this.service = service;
        this.quizContext = quizContext;
        this.quizSummaries = quizSummaries;
    }

    @GetMapping("/quizzes/courses")
    public List<QuizContextDto.Context> getQuizCourses() {
        return quizContext.courses();
    }

    @GetMapping("/quizzes/context")
    public QuizContextDto.Context getQuizContext(@RequestParam(required = false) String courseId,
            @RequestParam(required = false) String lessonId) {
        return quizContext.context(courseId, lessonId);
    }

    @GetMapping("/quizzes")
    public QuizSummaryPageDto.Result getQuizSummaries(@RequestParam(required = false) String courseId,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return quizSummaries.list(courseId, search, page, size);
    }

    @GetMapping("/quizzes/questions")
    public ResponseEntity<List<QuestionDto>> getQuizQuestions(@RequestParam String courseId,
            @RequestParam(required = false) String lessonId) {
        return ResponseEntity.ok(service.getQuizQuestions(courseId, lessonId));
    }

    @PostMapping("/quizzes/generate")
    public ResponseEntity<QuizGenerationResponseDto> generateQuiz(
            @Valid @RequestBody QuizGenerationRequestDto request) {
        return ResponseEntity.ok(service.generateQuiz(request));
    }

    @PostMapping("/questions")
    public ResponseEntity<QuestionDto> createQuestion(@RequestBody QuestionDto questionDto) {
        return ResponseEntity.ok(service.createQuestion(questionDto));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<QuestionDto> updateQuestion(@PathVariable Long id, @RequestBody QuestionDto questionDto) {
        return ResponseEntity.ok(service.updateQuestion(id, questionDto));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        service.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/quizzes/settings")
    public ResponseEntity<QuizSettingsDto> updateQuizSettings(@RequestBody QuizSettingsDto settings) {
        return ResponseEntity.ok(service.updateQuizSettings(settings));
    }

    @DeleteMapping("/quizzes")
    public ResponseEntity<Void> deleteQuiz(@RequestParam String courseId,
            @RequestParam(required = false) String lessonId) {
        service.deleteQuiz(courseId, lessonId);
        return ResponseEntity.noContent().build();
    }
}
