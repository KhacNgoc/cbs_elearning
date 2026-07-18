package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CommentDto;
import com.bidv.coretraining.dto.CommentRequest;
import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.TopicDto;

import java.util.List;

public interface CourseService {
    List<TopicDto> getAllTopics();
    TopicDto getTopicById(String id);
    LessonDto getLessonById(String id);
    CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username);
}
