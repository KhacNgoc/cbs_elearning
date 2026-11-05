package com.bidv.coretraining.repository;

import com.bidv.coretraining.dto.NotificationDraft;

import java.time.LocalDateTime;
import java.util.List;

/** Set-based queries for notifying the learners who completed a lesson. */
public interface NotificationNativeRepository {
    int countCompletedLearners(String lessonId);

    /** Inserts one COURSE_CONTENT_UPDATED notification per learner who completed the lesson. */
    int insertForCompletedLearners(String lessonId, String title, String message, String targetUrl);

    List<String> completedLearnerUsernames(String lessonId);

    /** One batched insert; returns generated ids in the order of the drafts. */
    List<Long> insertAll(List<NotificationDraft> drafts, LocalDateTime createdAt);
}
