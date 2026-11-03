package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.FlashcardLearningService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FlashcardController {
    private final FlashcardLearningService service;

    public FlashcardController(FlashcardLearningService service) {
        this.service = service;
    }

    @GetMapping("/lessons/{id}/flashcards")
    public FlashcardDto.Deck lessonDeck(@PathVariable String id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.lessonDeck(id, userDetails.getUsername());
    }

    @PostMapping("/flashcards/{id}/reviews")
    public FlashcardDto.Card review(@PathVariable Long id, @Valid @RequestBody FlashcardDto.ReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.review(id, request.rating(), userDetails.getUsername());
    }

    @PostMapping("/lessons/{id}/flashcards/mine")
    public FlashcardDto.Card createPersonal(@PathVariable String id,
            @Valid @RequestBody FlashcardDto.PersonalCardRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.createPersonal(id, request, userDetails.getUsername());
    }

    @PutMapping("/flashcards/mine/{id}")
    public FlashcardDto.Card updatePersonal(@PathVariable Long id,
            @Valid @RequestBody FlashcardDto.PersonalCardRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.updatePersonal(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/flashcards/mine/{id}")
    public ResponseEntity<Void> deletePersonal(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        service.deletePersonal(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/flashcards/summary")
    public FlashcardDto.Summary dueSummary(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.dueSummary(userDetails.getUsername());
    }

    @GetMapping("/users/me/flashcards/due")
    public FlashcardDto.ReviewQueue dueQueue(@RequestParam String courseId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.dueQueue(courseId, limit, userDetails.getUsername());
    }
}
