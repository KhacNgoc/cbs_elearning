package com.bidv.coretraining.service;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
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
 NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
 @Test void targetedEntryRejectsLessonFromAnotherCourseBeforeReturningMetadata() {
  var user=User.builder().id(1L).role("ROLE_ADMIN").build();when(auth.current()).thenReturn(user);when(users.findById(1L)).thenReturn(Optional.of(user));
  var course=Course.builder().id("C").name("Course").build();when(courses.findById("C")).thenReturn(Optional.of(course));
  when(lessons.findById("L")).thenReturn(Optional.of(Lesson.builder().id("L").course(Course.builder().id("OTHER").build()).build()));
  assertThatThrownBy(()->new LearnerQuizEntryService(auth,users,courses,lessons,jdbc).context("C","L")).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
  verifyNoInteractions(jdbc);verify(courses,never()).findAll();
 }
 @Test void inaccessibleCourseCannotReturnScoresOrMetadata() {
  var unit=OrganizationUnit.builder().id("U").active(true).build();var other=OrganizationUnit.builder().id("O").active(true).build();
  var user=User.builder().id(1L).role("ROLE_USER").organizationUnit(unit).build();when(auth.current()).thenReturn(user);when(users.findById(1L)).thenReturn(Optional.of(user));
  when(courses.findById("C")).thenReturn(Optional.of(Course.builder().id("C").name("Course").status("PUBLISHED").ownerUnit(other).build()));
  assertThatThrownBy(()->new LearnerQuizEntryService(auth,users,courses,lessons,jdbc).context("C",null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  verifyNoInteractions(jdbc);
 }
 @Test void successfulEntryReturnsOnlyTargetMetadataAndScopedScore() {
  var user=User.builder().id(1L).role("ROLE_ADMIN").build();when(auth.current()).thenReturn(user);when(users.findById(1L)).thenReturn(Optional.of(user));
  var course=Course.builder().id("C").name("Course").quizPassingScore(80).build();when(courses.findById("C")).thenReturn(Optional.of(course));
  var lesson=Lesson.builder().id("L").course(course).quiz(Quiz.builder().passingScore(65).build()).build();when(lessons.findById("L")).thenReturn(Optional.of(lesson));
  when(jdbc.queryForList(anyString(),anyMap())).thenReturn(List.of(Map.of("score",8,"total",10)));
  var result=new LearnerQuizEntryService(auth,users,courses,lessons,jdbc).context("C","L");
  assertThat(result.id()).isEqualTo("C");assertThat(result.lessonId()).isEqualTo("L");assertThat(result.quizPassingScore()).isEqualTo(65);
  assertThat(result.score()).isEqualTo(8);assertThat(result.total()).isEqualTo(10);
  verify(courses,never()).findAll();
 }

}
