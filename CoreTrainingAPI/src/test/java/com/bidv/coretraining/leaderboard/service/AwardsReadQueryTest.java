package com.bidv.coretraining.leaderboard.service;
import com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations;
import com.bidv.coretraining.profile.service.impl.ProfileAssessmentServiceImpl;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;import com.bidv.coretraining.leaderboard.entity.Badge;import com.bidv.coretraining.leaderboard.entity.UserBadge;import com.bidv.coretraining.training.entity.TrainingDomain;import com.bidv.coretraining.training.entity.TrainingProgram;import com.bidv.coretraining.user.entity.User;import jakarta.persistence.EntityManager;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;import org.springframework.context.annotation.Import;import static org.assertj.core.api.Assertions.*;
import com.bidv.coretraining.learner.repository.impl.LearnerDashboardQueryRepositoryImpl;
import com.bidv.coretraining.profile.repository.impl.ProfileAssessmentQueryRepositoryImpl;
import com.bidv.coretraining.profile.service.ProfileAssessmentService;
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="cbs.postgres.tests",matches="true")
@DataJpaTest(showSql=false,properties={"spring.config.location=classpath:access-test.yml","spring.sql.init.mode=never"})
@Import({LearnerDashboardQueryRepositoryImpl.class,LearnerDashboardSqlRelations.class,ProfileAssessmentQueryRepositoryImpl.class,ProfileAssessmentServiceImpl.class})
class AwardsReadQueryTest {
 @Autowired EntityManager em;@Autowired ProfileAssessmentService query;
 @MockBean AuthorizationService auth;
 private ProfileAssessmentService queryFor(User user){org.mockito.Mockito.when(auth.current()).thenReturn(user);return query;}
 @Test void inventoryIsPagedAndContainsOnlyTheAuthenticatedLearnersAwards(){
  var d=TrainingDomain.builder().code("AWD").name("Domain").build();em.persist(d);var p=TrainingProgram.builder().code("AWP").name("Program").domain(d).build();em.persist(p);var c=Course.builder().id("AWC").code("AWC").name("Course").status("PUBLISHED").program(p).build();em.persist(c);
  var u=User.builder().username("awards").fullName("Awards").password("x").role("ROLE_USER").points(42).build();em.persist(u);
  var other=User.builder().username("other-awards").fullName("Other").password("x").role("ROLE_USER").build();em.persist(other);
  for(int i=0;i<26;i++){var b=Badge.builder().id("badge"+i).title("Badge "+i).type("gold").icon("Award").course(c).build();em.persist(b);em.persist(UserBadge.builder().user(i==25?other:u).badge(b).unlockedAt(java.time.LocalDate.of(2026,10,1)).build());}em.flush();
  var first=queryFor(u).awards(0,10);var second=queryFor(u).awards(1,10);
  assertThat(first.total()).isEqualTo(25);assertThat(first.points()).isEqualTo(42);assertThat(first.items()).hasSize(10);assertThat(second.items()).hasSize(10);
  assertThat(second.items().stream().map(r->r.id())).doesNotContainAnyElementsOf(first.items().stream().map(r->r.id()).toList());
 }
}
