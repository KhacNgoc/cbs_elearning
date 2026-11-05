package com.bidv.coretraining.user.dto;

import com.bidv.coretraining.leaderboard.dto.UserBadgeDto;
import com.bidv.coretraining.lesson.dto.LessonProgressDto;
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
    private Map<String, Integer> quizScores; // courseId -> max score achieved
    private Map<String, Integer> quizTotals; // courseId -> total questions
    private Map<String, Integer> lessonQuizScores; // lessonId -> max score achieved
    private Map<String, Integer> lessonQuizTotals; // lessonId -> total questions
    private Map<String, LessonProgressDto> lessonProgress;
    private Integer points;
    private List<UserBadgeDto> unlockedBadges;
    private List<String> mandatoryCourses;
    private List<String> registeredCourses;
    private List<String> pendingCourses;
    private boolean mustChangePassword;
}
