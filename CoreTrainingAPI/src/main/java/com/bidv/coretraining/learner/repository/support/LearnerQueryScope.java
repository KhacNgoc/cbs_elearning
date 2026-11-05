package com.bidv.coretraining.learner.repository.support;
import java.util.*;
/** Resolved actor inputs; role scopes remain separate for SQL predicate compilation. */
public record LearnerQueryScope(Long id, boolean active, boolean system, boolean dynamic, String actorUnit,
        Set<String> scopeUnitIds, boolean unitAdmin, boolean instructor, Set<String> grantUnits, List<RoleScope> roles) {
    public record RoleScope(boolean editor, boolean assignedCoursesOnly, boolean legacyOwnerScope, Set<String> unitIds) {}
}
