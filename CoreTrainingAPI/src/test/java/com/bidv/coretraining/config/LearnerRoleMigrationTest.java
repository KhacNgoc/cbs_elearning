package com.bidv.coretraining.config;

import com.bidv.coretraining.entity.AccessRole;
import com.bidv.coretraining.repository.AccessRoleRepository;
import com.bidv.coretraining.service.PermissionCache;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LearnerRoleMigrationTest {
    @Test void classifiesOnlyExistingLearningOnlyRolesAndPreservesExplicitChoices() {
        var learner=AccessRole.builder().code("LEARNER").learningEnabled(true)
                .unitIds(new HashSet<>(Set.of("A"))).build();
        var manager=AccessRole.builder().code("MANAGER").learningEnabled(true)
                .permissions(new HashSet<>(Set.of("courses:read"))).unitIds(new HashSet<>(Set.of("A"))).build();
        var system=AccessRole.builder().code("SYSTEM").protectedRole(true).build();
        var explicit=AccessRole.builder().code("EXPLICIT").learnerRole(false).learningEnabled(true).build();
        var repository=mock(AccessRoleRepository.class);
        when(repository.findAll()).thenReturn(List.of(learner,manager,system,explicit));
        var migration=new LearnerRoleMigration(repository,mock(PermissionCache.class));
        migration.run();
        assertThat(learner.isLearnerRole()).isTrue();
        assertThat(learner.getUnitIds()).isEmpty();
        assertThat(manager.isLearnerRole()).isFalse();
        assertThat(manager.getUnitIds()).containsExactly("A");
        assertThat(system.isLearnerRole()).isFalse();
        assertThat(explicit.isLearnerRole()).isFalse();
        learner.setLearnerRole(false);
        migration.run();
        assertThat(learner.isLearnerRole()).isFalse();
    }
}
