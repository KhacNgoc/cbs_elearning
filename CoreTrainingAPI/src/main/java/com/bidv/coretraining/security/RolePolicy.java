package com.bidv.coretraining.security;

import com.bidv.coretraining.entity.AccessRole;
import java.util.Set;

/** Immutable cache value: permissions and scopes stay paired per role. */
public record RolePolicy(String id, String code, String name, boolean active, boolean protectedRole,
        boolean learningEnabled, boolean learnerRole, boolean assignedCoursesOnly, boolean legacyOwnerScope, Set<String> permissions,
        Set<String> unitIds) {
    public static RolePolicy of(AccessRole r) {
        return new RolePolicy(r.getId(), r.getCode(), r.getName(), r.isActive(), r.isProtectedRole(),
                r.isLearningEnabled(), r.isLearnerRole(), r.isAssignedCoursesOnly(), r.isLegacyOwnerScope(),
                Set.copyOf(r.getPermissions()), Set.copyOf(r.getUnitIds()));
    }
}
