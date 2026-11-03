package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizSessionDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;

public interface QuizSessionService {
    /** Starts a timed attempt, or resumes the learner's unfinished one so a reload cannot reset the clock. */
    QuizSessionDto start(String lessonId, String username);

    /** Rejects a timed submission without a live session; marks the session as used. */
    void consume(User user, Lesson lesson, String sessionId);
}
