package com.bidv.coretraining.service;

import com.bidv.coretraining.controller.AdminOptionsController;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.Test;
import com.bidv.coretraining.service.impl.AdminOptionsServiceImpl;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminOptionsLoadingTest {
    @Test void rejectsInvalidPagesBeforeAuthorizationOrRepositoryAccess() {
        var auth = mock(AuthorizationService.class);
        var units = mock(OrganizationUnitRepository.class);
        var programs = mock(TrainingProgramRepository.class);
        var courses = mock(CourseRepository.class);
        var domains = mock(TrainingDomainRepository.class);
        var service = new AdminOptionsServiceImpl(auth, units, programs, courses, domains);
        assertThatThrownBy(() -> service.options("invalid"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(auth, units, programs, courses, domains);
    }

    @Test void rejectsMissingReadPermissionBeforeLoadingOptions() {
        var auth = mock(AuthorizationService.class);
        var units = mock(OrganizationUnitRepository.class);
        var programs = mock(TrainingProgramRepository.class);
        var courses = mock(CourseRepository.class);
        var domains = mock(TrainingDomainRepository.class);
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(auth).requireOperation("courses", "read");
        var service = new AdminOptionsServiceImpl(auth, units, programs, courses, domains);
        assertThatThrownBy(() -> service.options("courses"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(units, programs, courses, domains);
        verify(auth, never()).current();
    }

    @Test void roleOptionsDoNotReadTrainingCollections() {
        var auth = mock(AuthorizationService.class);
        var units = mock(OrganizationUnitRepository.class);
        var programs = mock(TrainingProgramRepository.class);
        var courses = mock(CourseRepository.class);
        var domains = mock(TrainingDomainRepository.class);
        when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
        var controller = new AdminOptionsController(new AdminOptionsServiceImpl(auth, units, programs, courses, domains));
        assertThat(controller.options("roles").units()).isEqualTo(List.of());
        verifyNoInteractions(programs, courses, domains);
    }
    @Test void userOptionsDoNotReadTrainingCollections() {
        var auth = mock(AuthorizationService.class);
        var units = mock(OrganizationUnitRepository.class);
        var programs = mock(TrainingProgramRepository.class);
        var courses = mock(CourseRepository.class);
        var domains = mock(TrainingDomainRepository.class);
        when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
        var controller = new AdminOptionsController(new AdminOptionsServiceImpl(auth, units, programs, courses, domains));
        controller.options("users");
        verifyNoInteractions(programs, courses, domains);
    }
}
