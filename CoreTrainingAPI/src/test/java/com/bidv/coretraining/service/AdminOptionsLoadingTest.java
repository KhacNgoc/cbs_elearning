package com.bidv.coretraining.service;

import com.bidv.coretraining.controller.AdminOptionsController;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminOptionsLoadingTest {
    @Test void roleOptionsDoNotReadGroupsOrTrainingCollections() {
        var auth = mock(AuthorizationService.class);
        var units = mock(OrganizationUnitRepository.class);
        var programs = mock(TrainingProgramRepository.class);
        var courses = mock(CourseRepository.class);
        var domains = mock(TrainingDomainRepository.class);
        var groups = mock(UserGroupRepository.class);
        when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
        var controller = new AdminOptionsController(auth, units, programs, courses, domains);
        ReflectionTestUtils.setField(controller, "groups", groups);
        assertThat(controller.options("roles").get("units")).isEqualTo(List.of());
        verifyNoInteractions(groups, programs, courses, domains);
    }
    @Test void userOptionsDoNotReadTrainingCollectionsOrGroups() {
        var auth = mock(AuthorizationService.class);
        var units = mock(OrganizationUnitRepository.class);
        var programs = mock(TrainingProgramRepository.class);
        var courses = mock(CourseRepository.class);
        var domains = mock(TrainingDomainRepository.class);
        var groups = mock(UserGroupRepository.class);
        when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
        var controller = new AdminOptionsController(auth, units, programs, courses, domains);
        ReflectionTestUtils.setField(controller, "groups", groups);
        controller.options("users");
        verifyNoInteractions(groups, programs, courses, domains);
    }
}
