package com.bidv.coretraining.flashcard.service;

import com.bidv.coretraining.flashcard.dto.FlashcardDto;
import com.bidv.coretraining.flashcard.dto.FlashcardGenerationJobDto;

import java.util.List;

/** Instructor management of lesson flashcards, including AI drafting. */
public interface FlashcardAdminService {
    List<FlashcardDto.AdminCard> list(String lessonId);

    FlashcardDto.AdminCard create(String lessonId, FlashcardDto.CardRequest request);

    List<FlashcardDto.AdminCard> createBatch(String lessonId, FlashcardDto.BatchRequest request);

    FlashcardDto.AdminCard update(String lessonId, Long id, FlashcardDto.CardRequest request);

    void delete(String lessonId, Long id);

    List<FlashcardDto.AdminCard> updateStatus(String lessonId, FlashcardDto.StatusRequest request);

    FlashcardGenerationJobDto startGeneration(String lessonId, FlashcardDto.GenerationRequest request);

    FlashcardGenerationJobDto generationStatus(String lessonId, String jobId);
}
