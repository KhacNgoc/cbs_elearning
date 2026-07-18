package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.Comment;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CommentRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.TopicRepository;
import com.bidv.coretraining.service.CourseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CourseServiceImpl implements CourseService {

    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final CommentRepository commentRepository;
    private final CourseMapper courseMapper;

    public CourseServiceImpl(TopicRepository topicRepository, LessonRepository lessonRepository,
                             CommentRepository commentRepository, CourseMapper courseMapper) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.commentRepository = commentRepository;
        this.courseMapper = courseMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDto> getAllTopics() {
        List<Topic> topics = topicRepository.findAll();
        return courseMapper.toTopicDtoList(topics);
    }

    @Override
    @Transactional(readOnly = true)
    public TopicDto getTopicById(String id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", id));
        return courseMapper.toTopicDto(topic);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDto getLessonById(String id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        return courseMapper.toLessonDto(lesson);
    }

    @Override
    @Transactional
    public CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        String avatar = String.valueOf(username.charAt(0)).toUpperCase();

        Comment comment = Comment.builder()
                .id("comment_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4))
                .username(username)
                .avatar(avatar)
                .rating(commentRequest.getRating())
                .content(commentRequest.getContent())
                .createdAt(LocalDateTime.now())
                .lesson(lesson)
                .build();

        Comment savedComment = commentRepository.save(comment);
        return courseMapper.toCommentDto(savedComment);
    }
}
