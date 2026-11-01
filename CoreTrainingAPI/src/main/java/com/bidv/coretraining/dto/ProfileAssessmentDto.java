package com.bidv.coretraining.dto;
import java.util.*;
public final class ProfileAssessmentDto {
    public record Group(LearnerWorkspaceDto.Attempt latest, LearnerWorkspaceDto.Attempt best, long attemptCount,
            String courseName) {
    }
    public record Results(List<Group> items, long total, Integer bestScore, List<LegacyScore> legacyScores,
            long legacyTotal) {
    }
    public record LegacyScore(String courseId, String lessonId, String title, String courseName, int score, int total,
            java.time.LocalDateTime takenAt) {
    }
    public record Award(String id, String title, String type, String courseId, String courseName,
            java.time.LocalDate unlockedAt, String icon) {
    }
    public record Awards(int points, long total, long completedLessons, long totalLessons, List<Award> items) {
    }
    public record Competency(String id, String label, long count, Long value) {
    }
    public record Stats(long joined, long completed, long total, Integer percent, long notStarted, long inProgress,
            long complete, long mandatory, long mandatoryComplete, List<Competency> competencies) {
    }
    public record History(List<LearnerWorkspaceDto.Attempt> items, String nextCursor, boolean hasMore) {
    }
}
