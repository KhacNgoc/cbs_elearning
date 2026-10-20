package com.bidv.coretraining.service;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;

@DataJpaTest(properties={"spring.config.location=classpath:access-test.yml","spring.sql.init.mode=never"})
@Import(TrainingReportService.class)
@org.springframework.test.context.ContextConfiguration(classes=com.bidv.coretraining.CoreTrainingApiApplication.class)
class TrainingReportServiceTest {
    @Autowired TrainingReportService reports;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    @Autowired UserQuizScoreRepository scores;
    @MockBean AuthorizationService auth;
    User learner;Course course;Lesson first,second;
    @BeforeEach void setup(){
        var unit=units.save(OrganizationUnit.builder().id("UNIT").name("Unit").build());
        var domain=domains.save(TrainingDomain.builder().code("DOMAIN").name("Domain").build());
        var program=programs.save(TrainingProgram.builder().code("PROGRAM").name("Program").domain(domain).build());
        course=courses.save(Course.builder().id("COURSE").code("COURSE").name("Course").ownerUnit(unit).program(program).status("PUBLISHED").build());
        var chapter=chapters.save(Chapter.builder().course(course).code("CH").title("Chapter").status("PUBLISHED").chapterOrder(0).build());course.getChapters().add(chapter);
        first=lesson(chapter,"L1",0);second=lesson(chapter,"L2",1);
        learner=users.save(User.builder().username("learner").password("test").fullName("Learner").role("ROLE_USER").organizationUnit(unit).registeredCourses(new HashSet<>(Set.of(course))).build());
        when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
    }
    Lesson lesson(Chapter ch,String id,int order){var l=lessons.save(Lesson.builder().id(id).course(course).chapter(ch).title(id).status("PUBLISHED").lessonOrder(order).build());ch.getLessons().add(l);return l;}
    void score(Lesson lesson,int score,int day){var q=scores.save(UserQuizScore.builder().user(learner).course(course).lesson(lesson).score(score).total(10).takenAt(LocalDateTime.of(2026,10,day,10,0)).build());learner.getQuizScores().add(q);}
    @Test void completionWithoutQuizDoesNotRequireAnInventedExam(){
        learner.getCompletedLessons().addAll(Set.of(first,second));users.saveAndFlush(learner);
        var row=reports.snapshot().rows().get(0);
        assertThat(row.completed()).isTrue();assertThat(row.assessmentAverage()).isNull();assertThat(row.completedAt()).isNull();
        assertThat(row.attempts()).isEmpty();verify(auth).requireOperation("overview","read");
    }
    @Test void latestScoresAreAveragedPerAssessmentAndFailuresAreNotHiddenByAnotherExam(){
        score(first,10,1);score(first,4,3);score(second,6,2);
        var row=reports.snapshot().rows().get(0);
        assertThat(row.assessmentAverage()).isEqualTo(50.0);assertThat(row.assessmentPassed()).isFalse();
        assertThat(row.assessmentFirstPassed()).isFalse();assertThat(row.attempts()).hasSize(3);
        assertThat(row.started()).isTrue();assertThat(row.completed()).isFalse();
    }
    @Test void individualDeadlineOverridesCourseAndUnassignedUsersDoNotCreateRows(){
        course.setCompletionDeadline(LocalDate.of(2026,11,1));learner.getCourseDeadlines().put(course.getId(),LocalDate.of(2026,10,20));
        assertThat(reports.snapshot().rows()).hasSize(1);assertThat(reports.snapshot().rows().get(0).dueDate()).isEqualTo(LocalDate.of(2026,10,20));
        learner.getRegisteredCourses().clear();assertThat(reports.snapshot().rows()).isEmpty();
    }
    @Test void scopeExcludesLearnersOutsideTheViewersUnit(){
        var other=units.save(OrganizationUnit.builder().id("OTHER").name("Other").build());
        when(auth.current()).thenReturn(User.builder().role("ROLE_REPORT_VIEWER").organizationUnit(other).build());
        assertThat(reports.snapshot().rows()).isEmpty();
    }
}
