package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Deliberately no @Transactional on the test: the security principal must be detached,
// just as it is after the authentication filter finishes its repository transaction.
@SpringBootTest(classes=com.bidv.coretraining.CoreTrainingApiApplication.class,properties={"spring.config.location=classpath:access-test.yml",
        "spring.datasource.url=jdbc:h2:mem:learner-detached;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"})
class LearnerWorkspaceDetachedUserTest {
    @MockBean AuthorizationService auth;
    @MockBean org.springframework.mail.javamail.JavaMailSender sender;
    @Autowired LearnerWorkspaceService workspace;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserRepository users;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;

    @Test void reloadsDetachedPrincipalBeforeReadingLearningCollections() {
        TransactionTemplate tx=new TransactionTemplate(transactions);
        Long id=tx.execute(status->{
            var unit=units.save(OrganizationUnit.builder().id("DETACHED-U").name("Unit").build());
            var domain=domains.save(TrainingDomain.builder().code("DETACHED-D").name("Domain").build());
            var program=programs.save(TrainingProgram.builder().code("DETACHED-P").name("Program").domain(domain).build());
            var course=courses.save(Course.builder().id("DETACHED-C").code("DETACHED-C").name("Registered course").program(program).ownerUnit(unit).status("PUBLISHED").completionDeadline(LocalDate.of(2026,11,30)).build());
            var chapter=chapters.save(Chapter.builder().code("DETACHED-CH").title("Chapter").course(course).status("PUBLISHED").chapterOrder(0).build());
            course.getChapters().add(chapter);
            var lesson=Lesson.builder().id("DETACHED-L").title("Lesson").course(course).chapter(chapter).status("PUBLISHED").lessonOrder(0).build();
            lesson.ensureContent();lesson.setTextBody("Content");lessons.save(lesson);chapter.getLessons().add(lesson);course.getLessons().add(lesson);
            var user=User.builder().username("detached-learner").fullName("Learner").password("unused").role("ROLE_USER").organizationUnit(unit).build();
            user.getRegisteredCourses().add(course);user.getCourseDeadlines().put(course.getId(),LocalDate.of(2026,11,15));
            return users.save(user).getId();
        });
        User detached=tx.execute(status->users.findById(id).orElseThrow());
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(Hibernate.isInitialized(detached.getRegisteredCourses())).isFalse();
        assertThat(Hibernate.isInitialized(detached.getCompletedLessons())).isFalse();
        assertThat(Hibernate.isInitialized(detached.getQuizScores())).isFalse();
        assertThat(Hibernate.isInitialized(detached.getCourseDeadlines())).isFalse();
        assertThatThrownBy(()->detached.getRegisteredCourses().size()).isInstanceOf(LazyInitializationException.class);
        when(auth.current()).thenReturn(detached);

        var result=workspace.workspace();
        assertThat(result.courses()).hasSize(1);
        assertThat(result.courses().get(0).source()).isEqualTo("REGISTERED");
        assertThat(result.courses().get(0).dueDate()).isEqualTo(LocalDate.of(2026,11,15));
        assertThat(result.courses().get(0).learningStatus()).isEqualTo("NOT_STARTED");
        assertThat(result.resume().lessonId()).isEqualTo("DETACHED-L");
        assertThat(result.legacyScores()).isEmpty();
    }
}
