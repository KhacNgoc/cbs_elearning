package com.bidv.coretraining.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI flashcard drafts; nothing is saved until an instructor approves them. */
@Data
public class FlashcardGenerationResultDto {
    private List<GeneratedFlashcardDto> cards = new ArrayList<>();
    private List<QuizGenerationSourceDto> sources = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
