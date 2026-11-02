package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.dto.QuizGenerationResponseDto;

/** Business operations for QuizGenerationService. */
public interface QuizGenerationService {
    public QuizGenerationResponseDto generate(QuizGenerationRequestDto request);
}
