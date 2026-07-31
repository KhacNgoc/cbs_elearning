package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProgressDto {
    private List<String> completedLessons; // List of completed lesson IDs
    private Map<String, Integer> quizScores; // topicId -> max score achieved
    private Map<String, Integer> quizTotals; // topicId -> total questions
    private Map<String, Integer> lessonQuizScores; // lessonId -> max score achieved
    private Map<String, Integer> lessonQuizTotals; // lessonId -> total questions
    private Integer points;
    private List<UserBadgeDto> unlockedBadges;
    private String groupId;
    private String groupName;
    private List<String> mandatoryTopics;
    private List<String> registeredTopics;
    private List<String> pendingTopics;
}
