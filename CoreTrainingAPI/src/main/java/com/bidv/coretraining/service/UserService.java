package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.UserProgressDto;

public interface UserService {
    UserProgressDto getUserProgress(String username);
    UserProgressDto completeLesson(String username, String lessonId);
}
