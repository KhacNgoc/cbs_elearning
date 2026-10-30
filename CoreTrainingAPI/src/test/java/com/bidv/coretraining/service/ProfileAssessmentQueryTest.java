package com.bidv.coretraining.service;
import com.bidv.coretraining.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
// PostgreSQL is the production query engine; H2 2.2 loses bindings in nested parameterized CTEs.
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="spring.datasource.driver-class-name",matches="org.postgresql.Driver")
@DataJpaTest(showSql=false,properties={"spring.config.location=classpath:access-test.yml","spring.sql.init.mode=never"})
@Import({LearnerDashboardQuery.class,ProfileAssessmentQuery.class})
class ProfileAssessmentQueryTest {
 @Autowired EntityManager em;@Autowired ProfileAssessmentQuery query;
 @Test void groupsBeforePageKeepsRevisionBestAndLatestAndBoundsHistory(){
  var domain=TrainingDomain.builder().id("PD").code("PD").name("Domain").build();em.persist(domain);
  var program=TrainingProgram.builder().id("PP").code("PP").name("Program").domain(domain).build();em.persist(program);
  var user=User.builder().username("profile").fullName("Profile").password("x").role("ROLE_USER").build();em.persist(user);
  var course=Course.builder().id("PC").code("PC").name("Đào tạo").program(program).status("PUBLISHED").build();em.persist(course);user.getRegisteredCourses().add(course);
  var chapter=Chapter.builder().id("PCH").code("PCH").course(course).title("Chapter").status("PUBLISHED").build();em.persist(chapter);
  var lesson=Lesson.builder().id("PL").course(course).chapter(chapter).title("Lesson").status("PUBLISHED").build();em.persist(lesson);
  var quiz=Quiz.builder().id("PQ").lesson(lesson).title("Assessment").build();em.persist(quiz);
  for(int i=0;i<30;i++)em.persist(QuizAttempt.builder().id(String.format("A%02d",i)).user(user).quiz(quiz).revision(1).score(i==0?10:5).total(10).passingScore(80).passed(i==0).takenAt(LocalDateTime.of(2026,1,1,0,0).plusMinutes(i)).build());
  em.persist(QuizAttempt.builder().id("newrevision").user(user).quiz(quiz).revision(2).score(7).total(10).passingScore(80).passed(false).takenAt(LocalDateTime.of(2026,2,1,0,0)).build());
  var foreign=OrganizationUnit.builder().id("foreign").name("Foreign").build();em.persist(foreign);
  var hidden=Course.builder().id("hidden").code("hidden").name("Hidden").program(program).ownerUnit(foreign).build();em.persist(hidden);
  var hch=Chapter.builder().id("hch").code("hch").course(hidden).title("Hidden").build();em.persist(hch);var hl=Lesson.builder().id("hl").course(hidden).chapter(hch).title("Hidden").build();em.persist(hl);var hq=Quiz.builder().id("hq").lesson(hl).build();em.persist(hq);
  em.persist(UserQuizScore.builder().user(user).course(course).lesson(lesson).score(9).total(10).takenAt(LocalDateTime.of(2026,1,1,0,0)).build());
  em.persist(UserQuizScore.builder().user(user).course(course).score(8).total(10).takenAt(LocalDateTime.of(2026,1,1,0,0)).build());
  em.persist(QuizAttempt.builder().id("hiddenattempt").user(user).quiz(hq).revision(1).score(10).total(10).takenAt(LocalDateTime.of(2026,3,1,0,0)).build());em.flush();
  var page=query.results(user,0,1,"");assertThat(page.total()).isEqualTo(2);assertThat(page.legacyTotal()).isEqualTo(1);assertThat(page.legacyScores()).hasSize(1);assertThat(page.items()).hasSize(1);assertThat(page.items().get(0).latest().revision()).isEqualTo(2);
  assertThat(query.results(user,0,10,"dao tao").total()).isEqualTo(2);
  var second=query.results(user,1,1,"").items().get(0);assertThat(second.attemptCount()).isEqualTo(30);assertThat(second.latest().id()).isEqualTo("A29");assertThat(second.best().id()).isEqualTo("A00");
  var first=query.history(user,"PQ",1,null,20);assertThat(first.items()).hasSize(20);assertThat(first.hasMore()).isTrue();var older=query.history(user,"PQ",1,first.nextCursor(),20);assertThat(older.items()).hasSize(10);assertThat(older.items()).doesNotContainAnyElementsOf(first.items());
  assertThat(query.history(user,"hq",1,null,20).items()).isEmpty();assertThatThrownBy(()->query.history(user,"PQ",2,first.nextCursor(),20)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(query.stats(user).get("competencies").toString()).contains("value=70");assertThat(page.bestScore()).isEqualTo(100);
 }
}
