package com.bidv.coretraining.quiz.service;
import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.quiz.entity.Quiz;
import com.bidv.coretraining.quiz.repository.LearnerQuizEntryRepository;
import com.bidv.coretraining.quiz.service.impl.LearnerQuizEntryServiceImpl;
import com.bidv.coretraining.user.entity.OrganizationUnit;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class LearnerQuizEntryTest {
 AuthorizationService auth=mock(AuthorizationService.class);
 UserRepository users=mock(UserRepository.class);
 CourseRepository courses=mock(CourseRepository.class);
 LessonRepository lessons=mock(LessonRepository.class);
 LearnerQuizEntryRepository scores=mock(LearnerQuizEntryRepository.class);
 @Test void targetedEntryRejectsLessonFromAnotherCourseBeforeReturningMetadata() {
  var user=User.builder().id(1L).role("ROLE_ADMIN").build();when(auth.current()).thenReturn(user);when(users.findById(1L)).thenReturn(Optional.of(user));
  var course=Course.builder().id("C").name("Course").build();when(courses.findById("C")).thenReturn(Optional.of(course));
  when(lessons.findById("L")).thenReturn(Optional.of(Lesson.builder().id("L").course(Course.builder().id("OTHER").build()).build()));
  assertThatThrownBy(()->new LearnerQuizEntryServiceImpl(auth,users,courses,lessons,scores).context("C","L")).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
  verifyNoInteractions(scores);verify(courses,never()).findAll();
 }
 @Test void inaccessibleCourseCannotReturnScoresOrMetadata() {
  var unit=OrganizationUnit.builder().id("U").active(true).build();var other=OrganizationUnit.builder().id("O").active(true).build();
  var user=User.builder().id(1L).role("ROLE_USER").organizationUnit(unit).build();when(auth.current()).thenReturn(user);when(users.findById(1L)).thenReturn(Optional.of(user));
  when(courses.findById("C")).thenReturn(Optional.of(Course.builder().id("C").name("Course").status("PUBLISHED").ownerUnit(other).build()));
  assertThatThrownBy(()->new LearnerQuizEntryServiceImpl(auth,users,courses,lessons,scores).context("C",null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  verifyNoInteractions(scores);
 }
 @Test void successfulEntryReturnsOnlyTargetMetadataAndScopedScore() {
  var user=User.builder().id(1L).role("ROLE_ADMIN").build();when(auth.current()).thenReturn(user);when(users.findById(1L)).thenReturn(Optional.of(user));
  var course=Course.builder().id("C").name("Course").quizPassingScore(80).build();when(courses.findById("C")).thenReturn(Optional.of(course));
  var lesson=Lesson.builder().id("L").course(course).quiz(Quiz.builder().passingScore(65).build()).build();when(lessons.findById("L")).thenReturn(Optional.of(lesson));
  when(scores.latestScore(1L,"C","L")).thenReturn(Optional.of(new LearnerQuizEntryRepository.Score(8,10)));
  var result=new LearnerQuizEntryServiceImpl(auth,users,courses,lessons,scores).context("C","L");
  assertThat(result.id()).isEqualTo("C");assertThat(result.lessonId()).isEqualTo("L");assertThat(result.quizPassingScore()).isEqualTo(65);
  assertThat(result.score()).isEqualTo(8);assertThat(result.total()).isEqualTo(10);
  verify(courses,never()).findAll();
 }

}
