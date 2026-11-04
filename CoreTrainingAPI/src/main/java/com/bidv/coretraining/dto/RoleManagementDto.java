package com.bidv.coretraining.dto;

import java.util.*;

/** Data contracts for RoleManagementService. */
public final class RoleManagementDto {
    private RoleManagementDto() {}

    public record RoleDto(String id, String code, String name, String description, boolean active,
            boolean protectedRole, boolean learningEnabled, boolean learnerRole, boolean assignedCoursesOnly, Long version,
            Set<String> permissions, Set<String> unitIds, Set<Long> userIds) {
    }

    public record RoleRequest(String code, String name, String description, boolean active, boolean learningEnabled,
            Boolean learnerRole, Long version) {
    }

    public record GrantRequest(Set<String> permissions, Set<String> unitIds, Long version) {
    }

    public record MembersRequest(Set<Long> userIds, Long version) {
    }

    public record MemberDto(Long id, String username, String fullName, String unitId, Set<String> roles) {
    }

    public record PageDto(String id, String name, String route, int sortOrder, Set<String> actions) {
    }
}
