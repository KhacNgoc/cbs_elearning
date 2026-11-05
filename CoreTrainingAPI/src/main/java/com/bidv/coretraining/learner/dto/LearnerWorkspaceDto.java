package com.bidv.coretraining.learner.dto;

import com.bidv.coretraining.course.dto.CourseDto;
import java.time.*;
import java.util.*;

/** Data contracts for LearnerWorkspaceService. */
public final class LearnerWorkspaceDto {
    private LearnerWorkspaceDto() {}

    public record Resume(String courseId, String lessonId, String lessonTitle, String courseName,
                        String contentType,
                        Long documentId, Integer documentPage, Double scrollOffset, Double positionSeconds,
                        LocalDateTime lastAccessedAt) {
        }

    public record LearnerCourse(CourseDto course, String source, boolean mandatory, String learningStatus,
                        int completedCount, int totalCount, int percent, LocalDate dueDate,
                        LocalDateTime lastAccessedAt, Resume resume) {
        }

    public record Attempt(String id, String quizId, String courseId, String lessonId, String title, String domainId,
                        String domainName,
                        Integer revision, Integer score, Integer total, Integer passingScore, boolean passed,
                        LocalDateTime takenAt) {
        }

    public record LegacyScore(String courseId, String lessonId, String title, Integer score, Integer total,
                        LocalDateTime takenAt) {
        }

    public record Workspace(List<LearnerCourse> courses, Resume resume, List<Attempt> attempts,
                        List<LegacyScore> legacyScores) {
        }
}
