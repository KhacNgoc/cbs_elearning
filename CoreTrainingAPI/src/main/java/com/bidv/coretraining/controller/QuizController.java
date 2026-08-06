package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.QuizQuestionDto;
import com.bidv.coretraining.dto.QuizResultDto;
import com.bidv.coretraining.dto.QuizSubmitRequest;
import com.bidv.coretraining.dto.QuizReviewDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.QuizService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/lessons/{lessonId}/questions")
    public ResponseEntity<List<QuizQuestionDto>> getQuestionsForLesson(@PathVariable String lessonId) {
        return ResponseEntity.ok(quizService.getQuestionsForLesson(lessonId));
    }

    @PostMapping("/lessons/{lessonId}/submit")
    public ResponseEntity<QuizResultDto> submitLessonQuiz(
            @PathVariable String lessonId,
            @RequestBody QuizSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                quizService.submitLessonQuiz(lessonId, request, userDetails.getUsername())
        );
    }

    @GetMapping("/lessons/{lessonId}/review")
    public ResponseEntity<QuizReviewDto> getLessonQuizReview(
            @PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(quizService.getLessonQuizReview(lessonId, userDetails.getUsername()));
    }

    @GetMapping("/{topicId}/questions")
    public ResponseEntity<List<QuizQuestionDto>> getQuestionsForTopic(@PathVariable String topicId) {
        return ResponseEntity.ok(quizService.getQuestionsForTopic(topicId));
    }

    @PostMapping("/{topicId}/submit")
    public ResponseEntity<QuizResultDto> submitQuiz(
            @PathVariable String topicId,
            @RequestBody QuizSubmitRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        QuizResultDto response = quizService.submitQuiz(topicId, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{topicId}/review")
    public ResponseEntity<QuizReviewDto> getTopicQuizReview(
            @PathVariable String topicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(quizService.getTopicQuizReview(topicId, userDetails.getUsername()));
    }
}
