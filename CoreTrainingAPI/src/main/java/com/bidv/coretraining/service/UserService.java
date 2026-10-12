package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.dto.UserProfileDto;

public interface UserService {
    com.bidv.coretraining.dto.UserProfileDto updateProfile(String username, java.util.Map<String,String> fields);
    UserProfileDto getUserProfile(String username);
    UserProgressDto getUserProgress(String username);
    UserProgressDto completeLesson(String username, String lessonId);
    UserProgressDto resetLesson(String username, String lessonId);
    UserProgressDto registerCourse(String username, String courseId);
    void changePassword(String username, String newPassword);
}
