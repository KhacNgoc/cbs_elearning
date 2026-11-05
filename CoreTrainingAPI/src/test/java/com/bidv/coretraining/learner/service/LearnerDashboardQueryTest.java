package com.bidv.coretraining.learner.service;
import com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations;

import com.bidv.coretraining.course.entity.Chapter;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.learner.dto.LearnerDashboardDto;
import com.bidv.coretraining.learner.repository.LearnerDashboardQueryRepository;
import com.bidv.coretraining.learner.repository.impl.LearnerDashboardQueryRepositoryImpl;
import com.bidv.coretraining.learner.repository.support.LearnerQueryScopeFactory;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.quiz.entity.Question;
import com.bidv.coretraining.quiz.entity.Quiz;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import com.bidv.coretraining.training.entity.TrainingDomain;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.user.entity.OrganizationUnit;
import com.bidv.coretraining.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql=false, properties={"spring.config.location=classpath:access-test.yml", "spring.sql.init.mode=never"})
@Import({LearnerDashboardQueryRepositoryImpl.class,LearnerDashboardSqlRelations.class})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="cbs.postgres.tests",matches="true")
class LearnerDashboardQueryTest {
 @Autowired EntityManager em;
 @Autowired LearnerDashboardQueryRepository query;
 @Test void chatbotChoicesRequireEnrollmentUnlessActorCanReadCourseAsEditor() {
  var unit=OrganizationUnit.builder().id("CHAT").name("Chat unit").build();em.persist(unit);
  var d=TrainingDomain.builder().id("CD").code("CD").name("Domain").build();em.persist(d);
  var p=TrainingProgram.builder().id("CP").code("CP").name("Program").domain(d).build();em.persist(p);
  var user=User.builder().username("chat").fullName("Chat").password("x").role("ROLE_USER").organizationUnit(unit).build();em.persist(user);
  for(String id:java.util.List.of("D","E","V")){var c=Course.builder().id(id).code(id).name(id).program(p).ownerUnit(unit).status(id.equals("D")?"DRAFT":"PUBLISHED").build();em.persist(c);if(id.equals("E"))user.getRegisteredCourses().add(c);}
  em.flush();
  assertThat(query.courseChoices(LearnerQueryScopeFactory.from(user),"",50)).extracting(c->c.id()).containsExactly("E");
  // The assistant searches exactly the learner's picker scope: unregistered V and draft D stay hidden.
  assertThat(query.accessibleCourses(LearnerQueryScopeFactory.from(user),500)).extracting(c->c.id()).containsExactly("E");
  user.setRole("ROLE_ADMIN");
  assertThat(query.courseChoices(LearnerQueryScopeFactory.from(user),"",50)).extracting(c->c.id()).containsExactly("D","E","V");
 }
 @Test void filtersBeforePagingAndSummaryUsesWholeAuthorizedScope() {
  var d=TrainingDomain.builder().id("D").code("D").name("Domain").build();em.persist(d);
  var p=TrainingProgram.builder().id("P").code("P").name("Program").domain(d).build();em.persist(p);
  var u=User.builder().username("dashboard").fullName("Learner").password("x").role("ROLE_ADMIN").build();em.persist(u);
  for(int i=0;i<125;i++) {
   var c=Course.builder().id("C"+i).code("C"+i).name(i==14?"Đào tạo đặc biệt":"Course").status("PUBLISHED").program(p).build();em.persist(c);
   if(i%2==0) u.getRegisteredCourses().add(c);
  }
  em.flush();
  var summary=query.summary(LearnerQueryScopeFactory.from(u));
  assertThat(summary.joined()+summary.discover()).isEqualTo(125);
  var result=query.page(LearnerQueryScopeFactory.from(u),new LearnerDashboardDto.Filter("mine","dao tao dac biet","NOT_STARTED","","","","","",false,"name",1,6));
  assertThat(result.total()).isEqualTo(1);
  assertThat(result.items()).extracting(x->x.course().getId()).containsExactly("C14");
  assertThat(query.summary(LearnerQueryScopeFactory.from(u)).joined()).isEqualTo(63);
  assertThat(query.page(LearnerQueryScopeFactory.from(u),LearnerDashboardDto.Filter.defaults()).items()).hasSize(6);
  assertThat(result.items().get(0).course().getLessons()).isNull();
  var first=query.page(LearnerQueryScopeFactory.from(u),new LearnerDashboardDto.Filter("mine","","","","","","","",false,"name",1,6));
  var second=query.page(LearnerQueryScopeFactory.from(u),new LearnerDashboardDto.Filter("mine","","","","","","","",false,"name",2,6));
  assertThat(first.items()).extracting(x->x.course().getId()).containsExactly("C0","C10","C100","C102","C104","C106");
  assertThat(second.items()).extracting(x->x.course().getId()).doesNotContainAnyElementsOf(first.items().stream().map(x->x.course().getId()).toList());
  assertThat(query.choices(LearnerQueryScopeFactory.from(u),"mine").get("programId")).containsExactly(new LearnerDashboardDto.Choice("P","Program"));
  assertThat(query.page(LearnerQueryScopeFactory.from(u),new LearnerDashboardDto.Filter("mine","%","","","","","","",false,"name",1,6)).total()).isZero();
 }
 @Test void vietnameseAlphabetHasExplicitDatabaseOrdering() {
  var d=TrainingDomain.builder().id("DV").code("DV").name("Domain").build();em.persist(d);
  var p=TrainingProgram.builder().id("PV").code("PV").name("Program").domain(d).build();em.persist(p);
  var u=User.builder().username("sort").fullName("Learner").password("x").role("ROLE_ADMIN").build();em.persist(u);
  int i=0;
  for(String name:java.util.List.of("Bán hàng","Ân hạn","Ăn toàn","An toàn","Đào tạo","Dữ liệu","Á","À","Ả","Ã","Ạ","A","A\u0306")) {
   var c=Course.builder().id("V"+i).code("V"+i++).name(name).status("PUBLISHED").program(p).build();em.persist(c);u.getRegisteredCourses().add(c);
  }
  em.flush();
  var result=query.page(LearnerQueryScopeFactory.from(u),new LearnerDashboardDto.Filter("mine","","","","","","","",false,"name",1,20));
  assertThat(result.items()).extracting(x->x.course().getName()).containsExactly("A","À","Ả","Ã","Á","Ạ","An toàn","A\u0306","Ăn toàn","Ân hạn","Bán hàng","Dữ liệu","Đào tạo");
 }
 @Test void inactiveAncestryAndMixedRoleScopesCannotWidenThePage() {
  var root=OrganizationUnit.builder().id("R").name("Root").build();em.persist(root);
  var a=OrganizationUnit.builder().id("A").name("A").parent(root).build();em.persist(a);
  var b=OrganizationUnit.builder().id("B").name("B").build();em.persist(b);
  var d=TrainingDomain.builder().id("DA").code("DA").name("Domain").build();em.persist(d);
  var pa=TrainingProgram.builder().id("PA").code("PA").name("A").domain(d).build();pa.getUnits().add(a);em.persist(pa);
  var pb=TrainingProgram.builder().id("PB").code("PB").name("B").domain(d).build();pb.getUnits().add(b);em.persist(pb);
  var u=User.builder().username("scope").fullName("Learner").password("x").role("ROLE_USER").organizationUnit(a).dynamicAccessInitialized(true).build();em.persist(u);
  u.setPolicySnapshot(java.util.List.of(
   new com.bidv.coretraining.security.RolePolicy("r1","R1","R1",true,false,false,false,false,false,java.util.Set.of("courses:read"),java.util.Set.of("R")),
   new com.bidv.coretraining.security.RolePolicy("r2","R2","R2",true,false,false,false,false,false,java.util.Set.of("users:read"),java.util.Set.of("B"))));
  var ca=Course.builder().id("CA").code("CA").name("Z Visible").status("PUBLISHED").program(pa).build();em.persist(ca);
  var cb=Course.builder().id("CB").code("CB").name("A Hidden").status("PUBLISHED").program(pb).build();em.persist(cb);
  em.flush();
  var f=new LearnerDashboardDto.Filter("discover","","","","","","","",false,"name",1,1);
  assertThat(query.page(LearnerQueryScopeFactory.from(u),f).items()).extracting(x->x.course().getId()).containsExactly("CA");
  assertThat(query.page(LearnerQueryScopeFactory.from(u),f).total()).isEqualTo(1);
  root.setActive(false);em.flush();
  assertThat(query.page(LearnerQueryScopeFactory.from(u),f).total()).isZero();
 }

 @Test void legacyInstructorCannotSeeAnInactiveOwnerThroughAssignment() {
  var actorUnit=OrganizationUnit.builder().id("IU").name("Actor").build();em.persist(actorUnit);
  var owner=OrganizationUnit.builder().id("IO").name("Owner").active(false).build();em.persist(owner);
  var d=TrainingDomain.builder().id("ID").code("ID").name("Domain").build();em.persist(d);
  var p=TrainingProgram.builder().id("IP").code("IP").name("Program").domain(d).build();em.persist(p);
  var u=User.builder().username("instructor").fullName("Instructor").password("x").role("ROLE_INSTRUCTOR").organizationUnit(actorUnit).build();em.persist(u);em.flush();
  var c=Course.builder().id("IC").code("IC").name("Course").status("PUBLISHED").program(p).ownerUnit(owner).build();c.getInstructorIds().add(u.getId());em.persist(c);em.flush();
  assertThat(query.courseChoices(LearnerQueryScopeFactory.from(u),"",50)).isEmpty();
  owner.setActive(true);em.flush();assertThat(query.courseChoices(LearnerQueryScopeFactory.from(u),"",50)).hasSize(1);
 }

 @Test void completeRequiresNonemptyChaptersAndRoundedRequiredQuizPassBeforeStatusPaging() {
  var d=TrainingDomain.builder().id("CD").code("CD").name("Domain").build();em.persist(d);
  var p=TrainingProgram.builder().id("CP").code("CP").name("Program").domain(d).build();em.persist(p);
  var u=User.builder().username("completion").fullName("Learner").password("x").role("ROLE_ADMIN").build();em.persist(u);
  for(String id:java.util.List.of("DONE","FAILED","EMPTY")) {
   var c=Course.builder().id(id).code(id).name(id).status("PUBLISHED").program(p).build();em.persist(c);u.getRegisteredCourses().add(c);
   var ch=Chapter.builder().id(id+"CH").code("CH").title("Chapter").course(c).status("PUBLISHED").build();em.persist(ch);
   var l=Lesson.builder().id(id+"L").title("Lesson").course(c).chapter(ch).status("PUBLISHED").build();em.persist(l);u.getCompletedLessons().add(l);
   var q=Quiz.builder().id(id+"Q").lesson(l).passingScore(80).requiredForProgress(true).build();em.persist(q);l.setQuiz(q);
   var question=Question.builder().course(c).lesson(l).quiz(q).text("Q").correctAnswer(0).build();em.persist(question);q.getQuestions().add(question);
   em.persist(UserQuizScore.builder().user(u).course(c).lesson(l).score(id.equals("FAILED")?158:159).total(200).takenAt(java.time.LocalDateTime.now()).build());
   if(id.equals("EMPTY"))em.persist(Chapter.builder().id("EMPTYCH2").code("CH2").title("Empty").chapterOrder(1).course(c).status("PUBLISHED").build());
  }
  em.flush();
  var f=new LearnerDashboardDto.Filter("mine","","COMPLETED","","","","","",false,"name",1,1);
  assertThat(query.page(LearnerQueryScopeFactory.from(u),f).items()).extracting(x->x.course().getId()).containsExactly("DONE");
  assertThat(query.page(LearnerQueryScopeFactory.from(u),f).total()).isEqualTo(1);
  assertThat(query.summary(LearnerQueryScopeFactory.from(u)).complete()).isEqualTo(1);assertThat(query.summary(LearnerQueryScopeFactory.from(u)).inProgress()).isEqualTo(2);
 }

}
