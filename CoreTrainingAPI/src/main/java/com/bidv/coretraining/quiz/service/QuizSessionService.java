package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.quiz.dto.QuizSessionDto;
import com.bidv.coretraining.user.entity.User;

public interface QuizSessionService {
    /** Starts a timed attempt, or resumes the learner's unfinished one so a reload cannot reset the clock. */
    QuizSessionDto start(String lessonId, String username);

    /** Rejects a timed submission without a live session; marks the session as used. */
    void consume(User user, Lesson lesson, String sessionId);
}
