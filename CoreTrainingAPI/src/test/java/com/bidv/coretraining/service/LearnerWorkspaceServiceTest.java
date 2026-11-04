package com.bidv.coretraining.service;
import com.bidv.coretraining.enums.EnrollmentStatus;
import com.bidv.coretraining.service.impl.*;
import com.bidv.coretraining.mapper.CourseMapper;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearnerWorkspaceServiceTest {
    AuthorizationService auth=mock(AuthorizationService.class);
    UserRepository users=mock(UserRepository.class);
    CourseRepository courses=mock(CourseRepository.class);
    LessonProgressRepository progress=mock(LessonProgressRepository.class);
    QuizAttemptRepository attempts=mock(QuizAttemptRepository.class);
    CourseEnrollmentRequestRepository enrollments=mock(CourseEnrollmentRequestRepository.class);
    LearnerWorkspaceService service=new LearnerWorkspaceServiceImpl(auth,users,courses,Mappers.getMapper(CourseMapper.class),progress,attempts,enrollments);
    User user;Course course;Chapter chapter;Lesson first,second;
    @BeforeEach void setup(){
        var unit=OrganizationUnit.builder().id("U").name("Unit").build();
        var domain=TrainingDomain.builder().id("D").code("D").name("Domain").build();
        var program=TrainingProgram.builder().id("P").code("P").name("Program").domain(domain).build();
        course=Course.builder().id("C").code("C").name("Course").status("PUBLISHED").program(program).ownerUnit(unit).build();
        chapter=Chapter.builder().id("CH").code("CH").title("Chapter").chapterOrder(0).status("PUBLISHED").course(course).build();
        first=Lesson.builder().id("L1").title("First").status("PUBLISHED").lessonOrder(0).course(course).chapter(chapter).build();first.ensureContent();first.setTextBody("Text");
        second=Lesson.builder().id("L2").title("Second").status("PUBLISHED").lessonOrder(1).course(course).chapter(chapter).build();second.ensureContent();second.setTextBody("Text");
        chapter.getLessons().addAll(List.of(first,second));course.getChapters().add(chapter);
        user=User.builder().id(1L).username("learner").role("ROLE_USER").organizationUnit(unit).build();user.getRegisteredCourses().add(course);
        when(auth.current()).thenReturn(user);when(courses.findAll()).thenReturn(List.of(course));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(progress.findByUserId(1L)).thenReturn(List.of());when(attempts.findByUserIdOrderByTakenAtDesc(1L)).thenReturn(List.of());
        when(enrollments.findByUserIdAndStatus(1L,EnrollmentStatus.PENDING)).thenReturn(List.of());
    }
    @Test void completedLatestLessonContinuesToUnlockedNextLesson(){
        user.getCompletedLessons().add(first);
        when(progress.findByUserId(1L)).thenReturn(List.of(LessonProgress.builder().user(user).lesson(first).contentType("TEXT").updatedAt(LocalDateTime.now()).build()));
        var result=service.workspace();assertThat(result.resume().lessonId()).isEqualTo("L2");assertThat(result.courses().get(0).percent()).isEqualTo(50);
    }
    @Test void lockedLessonCannotBecomeResumeTargetEvenIfItHasStaleHistory(){
        when(progress.findByUserId(1L)).thenReturn(List.of(LessonProgress.builder().user(user).lesson(second).contentType("TEXT").updatedAt(LocalDateTime.now()).build()));
        assertThat(service.workspace().resume().lessonId()).isEqualTo("L1");
    }
    @Test void unfinishedRequiredQuizIsTheNextContent(){
        first.ensureQuiz();first.getQuiz().getQuestions().add(Question.builder().id(1L).build());
        when(progress.findByUserId(1L)).thenReturn(List.of(LessonProgress.builder().user(user).lesson(first).contentType("TEXT").textCompleted(true).contentCompleted(true).updatedAt(LocalDateTime.now()).build()));
        assertThat(service.workspace().resume().contentType()).isEqualTo("QUIZ");
    }
    @Test void completionAndEmptyCourseDoNotProduceAnActiveResume(){
        user.getCompletedLessons().addAll(List.of(first,second));assertThat(service.workspace().resume()).isNull();
        chapter.getLessons().clear();var result=service.workspace();assertThat(result.resume()).isNull();assertThat(result.courses().get(0).learningStatus()).isNotEqualTo("COMPLETED");
    }
}
