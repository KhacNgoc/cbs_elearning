package com.bidv.coretraining.flashcard.service;

import com.bidv.coretraining.flashcard.dto.FlashcardDto;
import com.bidv.coretraining.flashcard.dto.FlashcardGenerationJobDto;
import com.bidv.coretraining.lesson.entity.Lesson;

/** AI flashcard drafting in the RAG service, scoped to a lesson's knowledge sources. */
public interface FlashcardGenerationService {
    /** Lesson facts the RAG service needs, read up front so the slow call holds no DB connection. */
    record Target(String courseId, String courseName, String lessonId, String lessonName,
            java.util.List<java.util.Map<String, Object>> sources) {}

    /** Reads the target; call inside a transaction, after authorizing the lesson's course. */
    Target target(Lesson lesson);

    /** Starts drafting for a target read by {@link #target}; runs outside any transaction. */
    FlashcardGenerationJobDto startJob(Target target, FlashcardDto.GenerationRequest request);

    /** Lesson that owns a job started by {@link #startJob}, used for authorization. */
    String jobLessonId(String jobId);

    FlashcardGenerationJobDto jobStatus(String jobId);
}
