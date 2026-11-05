package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.quiz.dto.QuizGenerationJobDto;
import com.bidv.coretraining.quiz.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.quiz.dto.QuizGenerationResponseDto;

/** Business operations for QuizGenerationService. */
public interface QuizGenerationService {
    public QuizGenerationResponseDto generate(QuizGenerationRequestDto request);

    /** Starts generation in the RAG service and returns the initial progress snapshot. */
    QuizGenerationJobDto startJob(QuizGenerationRequestDto request);

    /** Course that owns a job started by {@link #startJob}, used for authorization. */
    String jobCourseId(String jobId);

    QuizGenerationJobDto jobStatus(String jobId);
}
