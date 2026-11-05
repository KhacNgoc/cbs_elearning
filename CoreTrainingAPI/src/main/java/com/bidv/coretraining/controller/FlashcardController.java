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
}
