package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.UserMapper;
import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.UserQuizScore;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final com.bidv.coretraining.repository.TopicRepository topicRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, LessonRepository lessonRepository,
                           com.bidv.coretraining.repository.TopicRepository topicRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.topicRepository = topicRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProgressDto getUserProgress(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return buildUserProgressDto(user);
    }

    @Override
    @Transactional
    public UserProgressDto completeLesson(String username, String lessonId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        if (!user.getCompletedLessons().contains(lesson)) {
            user.getCompletedLessons().add(lesson);
            user.setPoints(user.getPoints() + 100);
            user = userRepository.save(user);
        }

        return buildUserProgressDto(user);
    }

    @Override
    @Transactional
    public UserProgressDto registerTopic(String username, String topicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        com.bidv.coretraining.entity.Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        if (!user.getRegisteredTopics().contains(topic)) {
            user.getRegisteredTopics().add(topic);
            user = userRepository.save(user);
        }

        return buildUserProgressDto(user);
    }

    private UserProgressDto buildUserProgressDto(User user) {
        // Map completed lessons IDs
        java.util.List<String> completedLessons = user.getCompletedLessons().stream()
                .map(Lesson::getId)
                .collect(Collectors.toList());

        // Map quiz scores and totals
        Map<String, Integer> quizScores = new HashMap<>();
        Map<String, Integer> quizTotals = new HashMap<>();
        for (UserQuizScore score : user.getQuizScores()) {
            String topicId = score.getTopic().getId();
            quizScores.put(topicId, score.getScore());
            quizTotals.put(topicId, score.getTotal());
        }

        String groupId = null;
        String groupName = null;
        java.util.List<String> mandatoryTopics = new ArrayList<>();
        if (user.getUserGroup() != null) {
            groupId = user.getUserGroup().getId();
            groupName = user.getUserGroup().getName();
            mandatoryTopics = user.getUserGroup().getMandatoryTopics().stream()
                    .map(com.bidv.coretraining.entity.Topic::getId)
                    .collect(Collectors.toList());
        }

        java.util.List<String> registeredTopics = user.getRegisteredTopics().stream()
                .map(com.bidv.coretraining.entity.Topic::getId)
                .collect(Collectors.toList());

        return UserProgressDto.builder()
                .completedLessons(completedLessons)
                .quizScores(quizScores)
                .quizTotals(quizTotals)
                .points(user.getPoints())
                .unlockedBadges(userMapper.toUserBadgeDtoList(new ArrayList<>(user.getUnlockedBadges())))
                .groupId(groupId)
                .groupName(groupName)
                .mandatoryTopics(mandatoryTopics)
                .registeredTopics(registeredTopics)
                .build();
    }
}
