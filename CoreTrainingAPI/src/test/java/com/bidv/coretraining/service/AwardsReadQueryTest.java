package com.bidv.coretraining.service;
import com.bidv.coretraining.entity.*;import jakarta.persistence.EntityManager;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;import org.springframework.context.annotation.Import;import static org.assertj.core.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="cbs.postgres.tests",matches="true")
@DataJpaTest(showSql=false,properties={"spring.config.location=classpath:access-test.yml","spring.sql.init.mode=never"})
@Import({LearnerDashboardQuery.class,ProfileAssessmentQuery.class})
class AwardsReadQueryTest {
 @Autowired EntityManager em;@Autowired ProfileAssessmentQuery query;
 @Test void inventoryIsPagedAndContainsOnlyTheAuthenticatedLearnersAwards(){
  var d=TrainingDomain.builder().code("AWD").name("Domain").build();em.persist(d);var p=TrainingProgram.builder().code("AWP").name("Program").domain(d).build();em.persist(p);var c=Course.builder().id("AWC").code("AWC").name("Course").status("PUBLISHED").program(p).build();em.persist(c);
  var u=User.builder().username("awards").fullName("Awards").password("x").role("ROLE_USER").points(42).build();em.persist(u);
  var other=User.builder().username("other-awards").fullName("Other").password("x").role("ROLE_USER").build();em.persist(other);
  for(int i=0;i<26;i++){var b=Badge.builder().id("badge"+i).title("Badge "+i).type("gold").icon("Award").course(c).build();em.persist(b);em.persist(UserBadge.builder().user(i==25?other:u).badge(b).unlockedAt(java.time.LocalDate.of(2026,10,1)).build());}em.flush();
  var first=query.awards(u,0,10);var second=query.awards(u,1,10);
  assertThat(first.total()).isEqualTo(25);assertThat(first.points()).isEqualTo(42);assertThat(first.items()).hasSize(10);assertThat(second.items()).hasSize(10);
  assertThat(second.items().stream().map(r->r.get("id"))).doesNotContainAnyElementsOf(first.items().stream().map(r->r.get("id")).toList());
 }
}
