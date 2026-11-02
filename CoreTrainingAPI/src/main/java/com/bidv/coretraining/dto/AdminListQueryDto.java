package com.bidv.coretraining.dto;
import java.util.*;
public final class AdminListQueryDto {
 private AdminListQueryDto() {}
    public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {
    }

    public record NumberedPage<T>(List<T> items, long total, int page, int pageCount) {
    }

    public record ModerationPage(List<LessonReportDto> items, String nextCursor, boolean hasMore, long newCount) {
    }

    public record CourseChoice(String id, String code, String name) {
    }

    public record RoleSummary(String id, String code, String name, String description, boolean active,
            boolean protectedRole, boolean learningEnabled, boolean learnerRole, boolean assignedCoursesOnly,
            Long version,
            Set<String> permissions, Set<String> unitIds, Set<Long> userIds, long memberCount) {
    }

    public record RoleMemberRow(Long id, String username, String fullName, String unitId, Set<String> roles,
            boolean member, boolean selectable) {
    }

}
