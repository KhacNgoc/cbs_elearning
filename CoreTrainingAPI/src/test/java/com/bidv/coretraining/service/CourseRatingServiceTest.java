package com.bidv.coretraining.service;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.service.impl.*;
import com.bidv.coretraining.dto.CourseRatingDto;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.exception.BadRequestException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

@DataJpaTest(properties={"spring.config.location=classpath:access-test.yml","spring.sql.init.mode=never"})
@Import(CourseRatingServiceImpl.class)
@org.springframework.test.context.ContextConfiguration(classes=com.bidv.coretraining.CoreTrainingApiApplication.class)
class CourseRatingServiceTest {
    @Autowired CourseRatingService service;
    @Autowired CourseRatingRepository ratings;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    @MockBean AuthorizationService auth;
    User learner;Course course;Lesson lesson;
    @BeforeEach void setup(){
        var unit=units.save(OrganizationUnit.builder().id(UUID.randomUUID().toString()).name("Unit").build());
        var domain=domains.save(TrainingDomain.builder().code(UUID.randomUUID().toString()).name("Domain").build());
        var program=programs.save(TrainingProgram.builder().code(UUID.randomUUID().toString()).name("Program").domain(domain).build());
        course=courses.save(Course.builder().id("C-"+UUID.randomUUID()).code(UUID.randomUUID().toString()).name("Course").ownerUnit(unit).program(program).status("PUBLISHED").build());
        var chapter=chapters.save(Chapter.builder().course(course).code(UUID.randomUUID().toString()).title("Chapter").status("PUBLISHED").chapterOrder(0).build());course.getChapters().add(chapter);
        lesson=lessons.save(Lesson.builder().id("L-"+UUID.randomUUID()).title("Lesson").course(course).chapter(chapter).status("PUBLISHED").lessonOrder(0).build());chapter.getLessons().add(lesson);
        learner=users.save(User.builder().username("u-"+UUID.randomUUID()).password("test").fullName("Learner").role("ROLE_USER").organizationUnit(unit).registeredCourses(new HashSet<>(Set.of(course))).build());
        when(auth.current()).thenReturn(learner);when(auth.editableCourses()).thenReturn(List.of(course));
    }
    void complete(){learner.getCompletedLessons().add(lesson);LearningPolicy.recordCourseCompletion(learner,course);users.saveAndFlush(learner);}
    @Test void rejectsIncompleteAndInvalidRatings(){
        assertThat(LearningPolicy.ratingEligible(learner,course)).isFalse();
        assertThatThrownBy(()->service.save(course.getId(),new CourseRatingDto.Request(5,"",null))).isInstanceOf(BadRequestException.class);
        complete();
        assertThatThrownBy(()->service.save(course.getId(),new CourseRatingDto.Request(6,"",null))).isInstanceOf(BadRequestException.class);
    }
    @Test void oneReviewPerLearnerCanBeUpdatedAndLatestValueDrivesAverage(){
        complete();
        var first=service.save(course.getId(),new CourseRatingDto.Request(5,null,null));
        assertThat(first.mine().content()).isEmpty();assertThat(first.summary().count()).isEqualTo(1);assertThat(first.summary().average()).isEqualTo(5);
        var second=service.save(course.getId(),new CourseRatingDto.Request(2,"Cần cải thiện",first.mine().version()));
        assertThat(second.mine().id()).isEqualTo(first.mine().id());assertThat(second.summary().count()).isEqualTo(1);assertThat(second.summary().average()).isEqualTo(2);
        assertThat(second.summary().completed()).isEqualTo(1);assertThat(second.summary().responseRate()).isEqualTo(100);
        var report=service.report(null,true,0);assertThat(report.feedback()).hasSize(1);assertThat(report.low()).isEqualTo(1);assertThat(report.distribution().get(2)).isEqualTo(1);
        verify(auth).requireOperation("courses","read");
    }
    @Test void staleSubmissionDoesNotOverwriteOrCreateDuplicate(){
        complete();var first=service.save(course.getId(),new CourseRatingDto.Request(4,"",null));
        service.save(course.getId(),new CourseRatingDto.Request(3,"new",first.mine().version()));
        assertThatThrownBy(()->service.save(course.getId(),new CourseRatingDto.Request(1,"stale",first.mine().version()))).isInstanceOf(BadRequestException.class);
        assertThat(service.state(course.getId()).mine().stars()).isEqualTo(3);
    }
    @Test void noRatingIsNotZeroAndOutOfScopeCourseIsDenied(){
        assertThat(service.state(course.getId()).summary().average()).isNull();
        when(auth.editableCourses()).thenReturn(List.of());
        assertThat(service.report(null,false,0).feedback()).isEmpty();
        assertThatThrownBy(()->service.report(course.getId(),false,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void unregisteredUserCannotRateEvenWithCompletionFlags(){
        complete();learner.getRegisteredCourses().clear();
        assertThat(LearningPolicy.ratingEligible(learner,course)).isFalse();
    }
    @Test void requiredQuizMustBePassedEvenIfLessonWasMarkedComplete(){
        complete();lesson.ensureQuiz();lesson.getQuiz().getQuestions().add(Question.builder().build());lesson.getQuiz().setRequiredForProgress(true);
        assertThat(LearningPolicy.ratingEligible(learner,course)).isFalse();
    }
}
