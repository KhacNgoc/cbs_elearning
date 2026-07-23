package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.dto.TopicDto;
import com.bidv.coretraining.dto.UserDto;
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

    // Question CRUD
    QuestionDto createQuestion(QuestionDto questionDto);
    QuestionDto updateQuestion(Long id, QuestionDto questionDto);
    void deleteQuestion(Long id);

    // User CRUD
    List<UserDto> getAllUsers();
    UserDto createUser(UserDto userDto);
    UserDto updateUser(Long id, UserDto userDto);
    void deleteUser(Long id);
    void importUsersFromExcel(org.springframework.web.multipart.MultipartFile file);
    byte[] generateExcelTemplate();
}

