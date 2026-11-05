package com.bidv.coretraining.flashcard.controller;

import com.bidv.coretraining.flashcard.dto.FlashcardPlanDto;
import com.bidv.coretraining.flashcard.service.FlashcardPlanService;
import com.bidv.coretraining.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/flashcard-plans")
public class FlashcardPlanController {
    private final FlashcardPlanService service;

    public FlashcardPlanController(FlashcardPlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<FlashcardPlanDto.Plan> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.list(userDetails.getUsername());
    }

    @PostMapping
    public FlashcardPlanDto.Plan create(@Valid @RequestBody FlashcardPlanDto.Request request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.create(request, userDetails.getUsername());
    }

    @PutMapping("/{id}")
    public FlashcardPlanDto.Plan update(@PathVariable Long id, @Valid @RequestBody FlashcardPlanDto.Request request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.update(id, request, userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        service.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/cards")
    public FlashcardPlanDto.Session cards(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return service.session(id, userDetails.getUsername());
    }
}
