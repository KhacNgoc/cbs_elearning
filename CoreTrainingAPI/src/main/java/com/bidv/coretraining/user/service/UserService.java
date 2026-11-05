package com.bidv.coretraining.user.service;

import com.bidv.coretraining.user.dto.UserProfileDto;
import com.bidv.coretraining.user.dto.UserProgressDto;

public interface UserService {
    com.bidv.coretraining.user.dto.UserProfileDto updateProfile(String username, com.bidv.coretraining.user.dto.UpdateProfileRequest request);

    UserProfileDto getUserProfile(String username);

    UserProgressDto getUserProgress(String username);

    UserProgressDto completeLesson(String username, String lessonId);

    UserProgressDto resetLesson(String username, String lessonId);

    UserProgressDto registerCourse(String username, String courseId);

    void changePassword(String username, String newPassword);
}
