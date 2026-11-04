package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonChangeDto;
import com.bidv.coretraining.dto.LessonChangeLogDto;
import java.util.Optional;

public interface LessonChangeService {
    LessonChangeDto.CompletedLearnersCount countCompletedLearners(String lessonId);

    Optional<LessonChangeLogDto> getLatestChangeLog(String lessonId);

    /** Asks the RAG service to summarize a lesson edit; falls back to a generic summary on failure. */
    LessonChangeDto.AiSummary summarizeChanges(String lessonId, LessonChangeDto.AiSummaryRequest request);

    /** Records the change log entry and, when requested, notifies learners who completed the lesson. */
    LessonChangeDto.NotifyResult applyChangeAndNotify(String lessonId, LessonChangeDto.ChangeNotifyRequest request);
}
