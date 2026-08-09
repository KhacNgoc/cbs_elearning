package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;

import java.util.List;

public interface AdminService {
    // Topic CRUD
    TopicDto createTopic(TopicDto topicDto);
    TopicDto updateTopic(String id, TopicDto topicDto);
    void deleteTopic(String id);

    // Lesson CRUD
    LessonDto createLesson(LessonDto lessonDto);
    LessonDto updateLesson(String id, LessonDto lessonDto);
    void deleteLesson(String id);
    List<LessonDto> reorderLessons(String topicId, List<String> lessonIds);

    // Question CRUD
    List<QuestionDto> getQuizQuestions(String topicId, String lessonId);
    QuestionDto createQuestion(QuestionDto questionDto);
    QuestionDto updateQuestion(Long id, QuestionDto questionDto);
    void deleteQuestion(Long id);
    QuizSettingsDto updateQuizSettings(QuizSettingsDto settings);
    void deleteQuiz(String topicId, String lessonId);

    // User CRUD
    List<UserDto> getAllUsers();
    UserDto createUser(UserDto userDto);
    UserDto updateUser(Long id, UserDto userDto);
    void deleteUser(Long id);
    List<EnrollmentRequestDto> getEnrollmentRequests();
    EnrollmentRequestDto reviewEnrollmentRequest(
            Long id, boolean approve, String rejectionReason, String reviewedBy);
    void importUsersFromExcel(org.springframework.web.multipart.MultipartFile file);
    byte[] generateExcelTemplate();
    List<com.bidv.coretraining.entity.UserGroup> getAllUserGroups();
    DashboardStatsDto getDashboardStats();
}
