package com.bidv.coretraining.learner.service;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.learner.dto.LearnerDeadlineDto.Request;
import com.bidv.coretraining.learner.service.impl.LearnerDeadlineServiceImpl;
import com.bidv.coretraining.training.entity.TrainingDomain;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.training.repository.TrainingDomainRepository;
import com.bidv.coretraining.training.repository.TrainingProgramRepository;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.profiles.active=test", "spring.application.name=deadline-tests"})
@ContextConfiguration(classes = LearnerDeadlineServiceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LearnerDeadlineServiceTest {
    @org.springframework.context.annotation.Configuration @EnableAutoConfiguration
    @EntityScan("com.bidv.coretraining")
    @EnableJpaRepositories("com.bidv.coretraining")
    @Import(LearnerDeadlineServiceImpl.class)
    static class Config {
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
    }

    @Autowired LearnerDeadlineService service;
    @Autowired AuthorizationService authorization;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired PlatformTransactionManager transactionManager;
    Long userId;
    final LocalDate defaultDate = LocalDate.of(2027, 1, 1);

    @BeforeEach void fixture() {
        reset(authorization);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var domain = domains.save(TrainingDomain.builder().code("DEADLINE").name("Domain").build());
            var program = programs.save(TrainingProgram.builder().domain(domain).code("DEADLINE").name("Program").build());
            var course = courses.save(Course.builder().id("deadline-course").code("DEADLINE").name("Course")
                    .program(program).status("PUBLISHED").completionDeadline(defaultDate).build());
            var user = User.builder().username("deadline-user").password("unused").fullName("Learner")
                    .role("ROLE_USER").build();
            user.getRegisteredCourses().add(course);
            userId = users.save(user).getId();
        });
    }

    @AfterEach void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.deleteAll();
            courses.deleteAll();
            programs.deleteAll();
            domains.deleteAll();
        });
    }

    @Test void persistsDeadlineOverrideAndResetAcrossServiceTransactions() {
        var override = defaultDate.plusDays(14);
        service.save(userId, new Request("deadline-course", override));
        assertThat(service.list(userId)).singleElement().satisfies(deadline -> {
            assertThat(deadline.dueDate()).isEqualTo(override);
            assertThat(deadline.defaultDate()).isEqualTo(defaultDate);
            assertThat(deadline.overridden()).isTrue();
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(users.findById(userId).orElseThrow().getCourseDeadlines())
                        .containsEntry("deadline-course", override));
        service.save(userId, new Request("deadline-course", null));
        assertThat(service.list(userId)).singleElement().satisfies(deadline -> {
            assertThat(deadline.dueDate()).isEqualTo(defaultDate);
            assertThat(deadline.overridden()).isFalse();
        });
    }

    @Test void authorizesTargetBeforeLookingUpRequestedCourse() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(authorization).user(any(User.class));
        assertThatThrownBy(() -> service.save(userId, new Request("missing-course", defaultDate)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(authorization).user(argThat(user -> user.getId().equals(userId)));
    }
}
