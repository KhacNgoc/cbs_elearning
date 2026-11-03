package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.dto.FlashcardGenerationJobDto;
import com.bidv.coretraining.entity.Lesson;

/** AI flashcard drafting in the RAG service, scoped to a lesson's knowledge sources. */
public interface FlashcardGenerationService {
    /** Starts drafting; callers authorize the lesson's course first. */
    FlashcardGenerationJobDto startJob(Lesson lesson, FlashcardDto.GenerationRequest request);

    /** Lesson that owns a job started by {@link #startJob}, used for authorization. */
    String jobLessonId(String jobId);

    FlashcardGenerationJobDto jobStatus(String jobId);
}
