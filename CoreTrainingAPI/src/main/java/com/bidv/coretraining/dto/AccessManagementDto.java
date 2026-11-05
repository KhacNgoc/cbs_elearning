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

    public record CourseAccessDto(String ownerUnitId, Map<String, String> unitGrants, Set<Long> instructorIds) {
    }
}
