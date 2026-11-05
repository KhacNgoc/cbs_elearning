package com.bidv.coretraining.lesson.service;

import com.bidv.coretraining.lesson.dto.LessonProgressDto;
import com.bidv.coretraining.lesson.dto.LessonProgressRequest;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.user.entity.User;
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
