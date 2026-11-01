package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonProgressDto;
import com.bidv.coretraining.dto.LessonProgressRequest;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import java.util.Map;

/** Business operations for LessonProgressService. */
public interface LessonProgressService {
    public LessonProgressDto saveProgress(String username, String lessonId, LessonProgressRequest request);

    public void completeContent(String username, String lessonId);

    public void recordCompletion(User user, Lesson lesson);

    public boolean contentCompleted(User user, String lessonId);

    public Map<String, LessonProgressDto> getAllProgress(User user);

    public void resetProgress(Long userId, String lessonId);
}
