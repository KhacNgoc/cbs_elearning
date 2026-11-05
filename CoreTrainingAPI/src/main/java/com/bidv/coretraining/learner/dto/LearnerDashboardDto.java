package com.bidv.coretraining.learner.dto;
import com.bidv.coretraining.course.dto.CourseDto;
import java.util.*;
import java.time.*;
public final class LearnerDashboardDto {
    public record Filter(String tab, String search, String status, String domainId, String businessModule,
            String programId,
            String level, String source, boolean mandatory, String sort, int page, int size) {
        public static Filter defaults() {
            return new Filter("mine", "", "", "", "", "", "", "", false, "deadline", 1, 6);
        }
    }

    public record Card(CourseDto course, String source, boolean mandatory, String learningStatus, int completedCount,
            int totalCount, int percent, LocalDate dueDate, LocalDateTime lastAccessedAt,
            LearnerWorkspaceDto.Resume resume) {
    }

    public record Page(List<Card> items, long total, int page, int size) {
    }

    public record Summary(long joined, long discover, long notStarted, long inProgress, long complete, long mandatory,
            long mandatoryComplete, long completed, long total, Integer percent) {
    }

    public record Choice(String value, String label) {}
    public record CourseChoice(String id, String name) {}
    /** {@code choices} is keyed by filter name (status, source, domainId, ...). */
    public record Overview(Summary summary, Map<String,List<Choice>> choices, LearnerWorkspaceDto.Resume resume) {}
}
