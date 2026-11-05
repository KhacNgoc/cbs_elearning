package com.bidv.coretraining.flashcard.controller;

import com.bidv.coretraining.flashcard.dto.FlashcardDto;
import com.bidv.coretraining.flashcard.dto.FlashcardGenerationJobDto;
import com.bidv.coretraining.flashcard.service.FlashcardAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lesson flashcards; the /lessons path maps these operations to the courses permission page. */
@RestController
@RequestMapping("/api/admin/lessons/{lessonId}/flashcards")
public class AdminFlashcardController {
    private final FlashcardAdminService service;

    public AdminFlashcardController(FlashcardAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<FlashcardDto.AdminCard> list(@PathVariable String lessonId) {
        return service.list(lessonId);
    }

    @PostMapping
    public FlashcardDto.AdminCard create(@PathVariable String lessonId,
            @Valid @RequestBody FlashcardDto.CardRequest request) {
        return service.create(lessonId, request);
    }

    @PostMapping("/batch")
    public List<FlashcardDto.AdminCard> createBatch(@PathVariable String lessonId,
            @Valid @RequestBody FlashcardDto.BatchRequest request) {
        return service.createBatch(lessonId, request);
    }

    @PutMapping("/{id}")
    public FlashcardDto.AdminCard update(@PathVariable String lessonId, @PathVariable Long id,
            @Valid @RequestBody FlashcardDto.CardRequest request) {
        return service.update(lessonId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String lessonId, @PathVariable Long id) {
        service.delete(lessonId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/status")
    public List<FlashcardDto.AdminCard> updateStatus(@PathVariable String lessonId,
            @Valid @RequestBody FlashcardDto.StatusRequest request) {
        return service.updateStatus(lessonId, request);
    }

    @PostMapping("/generate/jobs")
    public ResponseEntity<FlashcardGenerationJobDto> startGeneration(@PathVariable String lessonId,
            @Valid @RequestBody FlashcardDto.GenerationRequest request) {
        return ResponseEntity.accepted().body(service.startGeneration(lessonId, request));
    }

    @GetMapping("/generate/jobs/{jobId}")
    public FlashcardGenerationJobDto generationStatus(@PathVariable String lessonId, @PathVariable String jobId) {
        return service.generationStatus(lessonId, jobId);
    }
}
