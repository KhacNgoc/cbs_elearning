package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.dto.TopicDto;

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
}
