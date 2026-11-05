package com.bidv.coretraining.admin.service;

import com.bidv.coretraining.admin.controller.AdminOptionsController;
import org.junit.jupiter.api.Test;
import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.admin.service.impl.AdminOptionsServiceImpl;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.training.repository.TrainingDomainRepository;
import com.bidv.coretraining.training.repository.TrainingProgramRepository;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.OrganizationUnitRepository;
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
