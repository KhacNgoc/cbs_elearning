package com.bidv.coretraining.config;

import com.bidv.coretraining.repository.AccessRoleRepository;
import com.bidv.coretraining.service.PermissionCache;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Classify existing roles once; never reclassify a role explicitly saved by an administrator. */
@Component
@Order(1)
public class LearnerRoleMigration implements CommandLineRunner {
    private final AccessRoleRepository roles;
    private final PermissionCache cache;

    public LearnerRoleMigration(AccessRoleRepository roles, PermissionCache cache) {
        this.roles = roles;
        this.cache = cache;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (var role : roles.findAll()) {
            if (role.getLearnerRole() != null) continue;
            boolean learner = role.isLearningEnabled() && !role.isProtectedRole()
                    && !role.isAssignedCoursesOnly() && role.getPermissions().isEmpty();
            role.setLearnerRole(learner);
            if (learner) {
                role.getUnitIds().clear();
                role.setLegacyOwnerScope(false);
            }
            roles.save(role);
        }
        cache.invalidateAfterCommit();
    }
}
