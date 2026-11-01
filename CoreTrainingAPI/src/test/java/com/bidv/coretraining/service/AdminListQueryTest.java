package com.bidv.coretraining.service;
import com.bidv.coretraining.enums.QuestionType;
import com.bidv.coretraining.enums.EnrollmentStatus;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false, properties = {"spring.config.location=classpath:access-test.yml", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
@org.springframework.test.context.ContextConfiguration(classes = com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({com.bidv.coretraining.service.impl.AdminListQueryServiceImpl.class, com.bidv.coretraining.repository.impl.AdminListQueryRepositoryImpl.class})
class AdminListQueryTest {
    @Autowired AdminListQueryService query;
    @Autowired EntityManager em;
    @MockBean AuthorizationService auth;
    OrganizationUnit root, child, foreign, inactive;
    User actor;
    @BeforeEach void setup() {
        root = unit("A", null, true); child = unit("A-child", root, true);
        foreign = unit("B", null, true); inactive = unit("closed", root, false);
        actor = User.builder().id(999L).role("ROLE_UNIT_ADMIN").organizationUnit(root).build();
        when(auth.current()).thenReturn(actor);
    }
    OrganizationUnit unit(String id, OrganizationUnit parent, boolean active) {
        var result = OrganizationUnit.builder().id(id).name(id).parent(parent).active(active).build(); em.persist(result); return result;
    }
    Course course(String id, OrganizationUnit owner, OrganizationUnit programUnit) {
        var domain = TrainingDomain.builder().code(id).name(id).build(); em.persist(domain);
        var program = TrainingProgram.builder().code(id).name(id).domain(domain).units(new HashSet<>(Set.of(programUnit))).build(); em.persist(program);
        var c = Course.builder().id(id).code(id).name(id).ownerUnit(owner).program(program).status("DRAFT").build(); em.persist(c); return c;
    }
    void bank(Course course, String code) {
        em.persist(BankQuestion.builder().course(course).code(code).text("needle").type(QuestionType.SINGLE_CHOICE)
                .options(new ArrayList<>(List.of("A", "B"))).correctAnswers(new ArrayList<>(List.of(0)))
                .difficulty(BankQuestion.Difficulty.EASY).status(BankQuestion.Status.ACTIVE).updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build());
    }
    @Test void questionPagesAuthorizeBeforeLimitAndBreakTimestampTies() {
        var allowed = course("allowed", child, child); var outside = course("foreign", foreign, foreign);
        bank(allowed, "A1"); bank(outside, "B1"); bank(allowed, "A2"); bank(outside, "B2"); bank(allowed, "A3"); em.flush();
        var first = query.bank("needle", "ACTIVE", "EASY", null, 2);
        assertThat(first.items()).hasSize(2); assertThat(first.hasMore()).isTrue();
        assertThat(first.items()).allMatch(q -> q.courseId().equals("allowed"));
        var next = query.bank("needle", "ACTIVE", "EASY", first.nextCursor(), 2);
        assertThat(next.items()).hasSize(1); assertThat(next.hasMore()).isFalse();
        assertThat(next.items()).noneMatch(q -> first.items().stream().anyMatch(p -> p.id().equals(q.id())));
        assertThatThrownBy(() -> query.bank("changed", "ACTIVE", "EASY", first.nextCursor(), 2)).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        assertThatThrownBy(() -> query.bank("", "", "", null, 101)).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
    }
    @Test void dynamicCourseScopesKeepPermissionsPairedWithTheirOwnRole() {
        var allowed = course("program", foreign, child); var denied = course("outside", child, foreign);
        bank(allowed, "P1"); bank(denied, "P2");
        actor.setDynamicAccessInitialized(true);
        actor.setPolicySnapshot(List.of(
                new RolePolicy("read", "read", "read", true, false, false, false, false, false, Set.of("exams:read"), Set.of(root.getId())),
                new RolePolicy("other", "other", "other", true, false, false, false, false, true, Set.of("users:read"), Set.of(foreign.getId()))));
        em.flush();
        assertThat(query.bank("", "", "", null, 10).items()).extracting(q -> q.courseId()).containsExactly("program");
    }
    @Test void userCountAndPagesExcludeInactiveAncestryAndSystemAccounts() {
        user("Z-root", root, "ROLE_USER"); user("A-child", child, "ROLE_USER");
        user("outside", foreign, "ROLE_USER"); user("closed", inactive, "ROLE_USER"); user("admin", child, "ROLE_ADMIN"); em.flush();
        var result = query.users("", "", "", 1, 1);
        assertThat(result.total()).isEqualTo(2); assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.items()).hasSize(1); assertThat(result.items().get(0).getUsername()).isEqualTo("A-child");
    }
    @Test void roleSummariesBoundMembersAndMemberPagesRetainAssignmentFlags() {
        actor = User.builder().id(999L).role("ROLE_ADMIN").build(); when(auth.current()).thenReturn(actor);
        var role = AccessRole.builder().code("ROLE_X").name("Role X").learnerRole(false).build(); em.persist(role);
        for (int i = 0; i < 13; i++) {
            var target = User.builder().username("member" + i).fullName("Member " + i).password("unused").role("ROLE_USER").organizationUnit(child).build();
            target.getAssignedRoles().add(role); em.persist(target);
        }
        em.flush();
        var summary = query.roles("Role X", "", 1, 10, false);
        assertThat(summary.items()).hasSize(1); assertThat(summary.items().get(0).memberCount()).isEqualTo(13);
        assertThat(summary.items().get(0).userIds()).isEmpty();
        var members = query.roleMembers(role.getId(), "", 1, 5);
        assertThat(members.total()).isEqualTo(13); assertThat(members.items()).hasSize(5).allMatch(m -> m.member());
    }
    @Test void enrollmentCursorAuthorizesTargetVisibilityBeforeLimit() {
        var learner = User.builder().username("learner").fullName("Learner").password("unused").role("ROLE_USER").organizationUnit(child).build(); em.persist(learner);
        var local = course("local", child, child); var denied = course("denied", foreign, foreign);
        var registered = course("registered", foreign, foreign); learner.getRegisteredCourses().add(registered);
        var granted = course("granted", foreign, foreign); granted.getUnitGrants().put(root.getId(), "OPTIONAL");
        var permitted = new HashSet<Long>();
        for (var c : List.of(local, denied, registered, granted)) {
            var request = CourseEnrollmentRequest.builder().user(learner).course(c).status(EnrollmentStatus.PENDING).requestedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build(); em.persist(request);
            if (AccessPolicy.visible(learner, c)) permitted.add(request.getId());
        }
        var outsider = User.builder().username("outsider").fullName("Outsider").password("unused").role("ROLE_USER").organizationUnit(foreign).build(); em.persist(outsider);
        em.persist(CourseEnrollmentRequest.builder().user(outsider).course(denied).status(EnrollmentStatus.PENDING).requestedAt(LocalDateTime.of(2027, 1, 1, 0, 0)).build()); em.flush();
        var first = query.enrollments("", "PENDING", null, 2); assertThat(first.items()).hasSize(2); assertThat(first.hasMore()).isTrue();
        var next = query.enrollments("", "PENDING", first.nextCursor(), 2); assertThat(next.items()).hasSize(1); assertThat(next.hasMore()).isFalse();
        var actual = new HashSet<Long>(); first.items().forEach(r -> actual.add(r.getId())); next.items().forEach(r -> actual.add(r.getId()));
        assertThat(actual).isEqualTo(permitted);
    }
    void user(String name, OrganizationUnit unit, String role) {
        em.persist(User.builder().username(name).fullName(name).password("unused").organizationUnit(unit).role(role).build());
    }
}
