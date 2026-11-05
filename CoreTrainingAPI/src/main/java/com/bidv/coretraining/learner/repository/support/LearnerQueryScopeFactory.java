package com.bidv.coretraining.learner.repository.support;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.entity.User;
import java.util.Set;
import java.util.List;
/** Resolves policy decisions before repositories compile relational scope predicates. */
public final class LearnerQueryScopeFactory {
    private LearnerQueryScopeFactory() {}
    public static LearnerQueryScope from(User u) {
        boolean system=AccessPolicy.system(u);
        var roles = !u.isDynamicAccessInitialized() || system ? List.<LearnerQueryScope.RoleScope>of() : AccessPolicy.policies(u).stream()
            .filter(r -> r.active() && !r.learnerRole() && (r.permissions().contains("courses:read") || r.permissions().contains("courses:update")))
            .map(r -> new LearnerQueryScope.RoleScope(r.permissions().contains("courses:read"), r.assignedCoursesOnly(), r.legacyOwnerScope(), r.unitIds())).toList();
        var grants = u.getOrganizationUnit()!=null && AccessPolicy.activeUnit(u.getOrganizationUnit()) ? AccessPolicy.ancestors(u.getOrganizationUnit()) : Set.<String>of();
        return new LearnerQueryScope(u.getId(),u.activeAccount(),system,u.isDynamicAccessInitialized(),
            u.getOrganizationUnit()==null?null:u.getOrganizationUnit().getId(),u.getScopeUnitIds(),u.hasRole("ROLE_UNIT_ADMIN"),u.hasRole("ROLE_INSTRUCTOR"),grants,roles);
    }
}
