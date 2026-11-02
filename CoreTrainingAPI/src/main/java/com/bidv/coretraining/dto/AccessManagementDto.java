package com.bidv.coretraining.dto;

import java.util.*;

/** Data contracts for AccessManagementService. */
public final class AccessManagementDto {
    private AccessManagementDto() {}

    public record UnitDto(String id, String name, String type, String parentId, boolean active,
            Set<String> allowedActions) {
        public UnitDto(String id, String name, String type, String parentId, boolean active) {
            this(id, name, type, parentId, active, Set.of());
        }
    }

    public record GroupDto(String id, String name, String unitId, Set<String> mandatoryCourseIds,
            Set<String> allowedActions, long memberCount) {
        public GroupDto(String id, String name, String unitId, Set<String> courses) {
            this(id, name, unitId, courses, Set.of(), 0);
        }
    }

    public record GroupMemberDto(Long id, String username, String fullName, String employeeId,
            String unitName, String status, boolean member, boolean selectable) {}

    public record GroupMembersPage(List<GroupMemberDto> items, long total, int page, int pageCount,
            long memberCount) {}

    public record MembershipChanges(Set<Long> addUserIds, Set<Long> removeUserIds) {}

    public record CourseAccessDto(String ownerUnitId, Map<String, String> unitGrants, Set<Long> instructorIds) {
    }
}
