package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardPlanDto;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.service.impl.FlashcardLearningServiceImpl;
import com.bidv.coretraining.service.impl.FlashcardPlanServiceImpl;
import com.bidv.coretraining.service.impl.LearningAccessServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** The batched flashcard reads must keep answering exactly what the per-lesson access gates answer. */
@DataJpaTest(showSql = false, properties = { "spring.config.location=classpath:access-test.yml",
        "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.generate_statistics=true" })
@ContextConfiguration(classes = com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({ LearningAccessServiceImpl.class, FlashcardLearningServiceImpl.class, FlashcardPlanServiceImpl.class,
        com.bidv.coretraining.repository.impl.FlashcardPlanNativeRepositoryImpl.class,
        com.bidv.coretraining.repository.impl.LearnerDashboardQueryRepositoryImpl.class,
        com.bidv.coretraining.repository.support.LearnerDashboardSqlRelations.class })
class FlashcardAccessParityTest {
    @Autowired LearningAccessService access;
    @Autowired FlashcardLearningService learning;
    @Autowired FlashcardPlanService plans;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @MockBean NotificationService notifications;

    private final String run = UUID.randomUUID().toString().substring(0, 8);

    private OrganizationUnit unit(String name, OrganizationUnit parent, boolean active) {
        var unit = OrganizationUnit.builder().id(run + name).name(name).parent(parent).active(active).build();
        em.persist(unit);
        return unit;
    }

    private User user(String name, OrganizationUnit unit, String role) {
        var user = User.builder().username(run + name).fullName(name).password("x").role(role).organizationUnit(unit)
                .email(run + name + "@test.local").build();
        em.persist(user);
        return user;
    }

    private TrainingProgram program(String name, TrainingDomain domain) {
        var program = TrainingProgram.builder().code(run + name).name(name).domain(domain).build();
        em.persist(program);
        return program;
    }

    private Course course(String name, TrainingProgram program, OrganizationUnit owner, String status) {
        var course = Course.builder().id(run + name).code(run + name).name(name).program(program).ownerUnit(owner)
                .status(status).build();
        em.persist(course);
        return course;
    }

    private User dynamic(String name, OrganizationUnit unit, AccessRole role) {
        var user = user(name, unit, "UNASSIGNED");
        user.setDynamicAccessInitialized(true);
        user.getAssignedRoles().add(role);
        return user;
    }

    private AccessRole role(String code, boolean learner, boolean assignedOnly, boolean legacyOwner, Set<String> perms,
            Set<String> units) {
        var role = AccessRole.builder().code(run + code).name(code).learnerRole(learner).assignedCoursesOnly(assignedOnly)
                .legacyOwnerScope(legacyOwner).permissions(new HashSet<>(perms)).unitIds(new HashSet<>(units)).build();
        em.persist(role);
        return role;
    }

    /** Learnable id to its editor flag, as requireCourse decides one course at a time. */
    private Map<String, Boolean> byRequireCourse(User user, List<Course> courses) {
        Map<String, Boolean> result = new HashMap<>();
        for (Course course : courses) {
            if (!"PUBLISHED".equals(course.getStatus()))
                continue;
            try {
                result.put(course.getId(), access.requireCourse(user, course));
            } catch (AccessDeniedException denied) {
                // Not learnable.
            }
        }
        return result;
    }

    private Map<String, Boolean> byLearnableCourses(User user) {
        Map<String, Boolean> result = new HashMap<>();
        access.learnableCourses(user).forEach(item -> result.put(item.id(), item.editor()));
        return result;
    }

    @Test void learnableCoursesMatchRequireCourseAcrossGrantsRolesAndUnitStates() {
        var root = unit("root", null, true);
        var child = unit("child", root, true);
        var other = unit("other", null, true);
        var closed = unit("closed", null, false);

        var domain = TrainingDomain.builder().code(run + "d").name("D").build();
        em.persist(domain);
        var grantedDomain = TrainingDomain.builder().code(run + "dg").name("DG").build();
        grantedDomain.getUnitGrants().put(root.getId(), "MANDATORY");
        em.persist(grantedDomain);
        var plain = program("plain", domain);
        var ownedProgram = program("owned", domain);
        ownedProgram.getUnits().add(other);
        var grantedProgram = program("granted", domain);
        grantedProgram.getUnitGrants().put(root.getId(), "MANDATORY");
        var inGrantedDomain = program("inDomain", grantedDomain);

        var registered = course("registered", plain, other, "PUBLISHED");
        var mandatory = course("mandatory", plain, other, "PUBLISHED");
        mandatory.getUnitGrants().put(child.getId(), "MANDATORY");
        var optional = course("optional", plain, other, "PUBLISHED");
        optional.getUnitGrants().put(child.getId(), "OPTIONAL");
        var optionalOverride = course("override", grantedProgram, other, "PUBLISHED");
        optionalOverride.getUnitGrants().put(child.getId(), "OPTIONAL");
        var byProgram = course("byProgram", grantedProgram, other, "PUBLISHED");
        var byDomain = course("byDomain", inGrantedDomain, other, "PUBLISHED");
        var owned = course("owned", ownedProgram, child, "PUBLISHED");
        var draft = course("draft", plain, other, "DRAFT");
        var taught = course("taught", plain, other, "PUBLISHED");
        var noOwner = course("noOwner", plain, null, "PUBLISHED");
        var noOwnerRegistered = course("noOwnerReg", plain, null, "PUBLISHED");
        var all = List.of(registered, mandatory, optional, optionalOverride, byProgram, byDomain,
                owned, draft, taught, noOwner, noOwnerRegistered);

        var learner = user("learner", child, "ROLE_USER");
        learner.getRegisteredCourses().addAll(List.of(registered, draft));
        var noUnit = user("noUnit", null, "ROLE_USER");
        noUnit.getRegisteredCourses().add(noOwnerRegistered);
        var inClosedUnit = user("inClosed", closed, "ROLE_USER");
        inClosedUnit.getRegisteredCourses().add(registered);
        var locked = user("locked", child, "ROLE_USER");
        locked.setStatus("LOCKED");
        locked.getRegisteredCourses().add(registered);
        var admin = user("admin", other, "ROLE_ADMIN");
        var unitAdmin = user("unitAdmin", other, "ROLE_UNIT_ADMIN");
        var instructor = user("instructor", other, "ROLE_INSTRUCTOR");
        taught.getInstructorIds().add(instructor.getId());
        var dynamicLearner = dynamic("dynLearner", child, role("l", true, false, false, Set.of(), Set.of()));
        dynamicLearner.getRegisteredCourses().add(registered);
        var dynamicNoLearning = dynamic("dynNoLearn", child, role("n", false, false, false, Set.of("overview:read"), Set.of()));
        dynamicNoLearning.getAssignedRoles().iterator().next().setLearningEnabled(false);
        dynamicNoLearning.getRegisteredCourses().add(registered);
        var ownerReader = dynamic("dynOwner", root, role("o", false, false, true, Set.of("courses:read"), Set.of(other.getId())));
        var programReader = dynamic("dynProgram", root, role("p", false, false, false, Set.of("courses:read"), Set.of(other.getId())));
        var assigned = dynamic("dynAssigned", root, role("a", false, true, false, Set.of("courses:read"), Set.of()));
        taught.getInstructorIds().add(assigned.getId());
        em.flush();

        var users = List.of(learner, noUnit, inClosedUnit, locked, admin, unitAdmin, instructor, dynamicLearner,
                dynamicNoLearning, ownerReader, programReader, assigned);
        for (User user : users) {
            em.flush();
            assertThat(byLearnableCourses(user)).as(user.getFullName()).isEqualTo(byRequireCourse(user, all));
        }
        // Guards the comparison itself: these users really do reach different courses.
        assertThat(byLearnableCourses(learner).keySet()).containsExactlyInAnyOrder(registered.getId(), mandatory.getId(),
                byProgram.getId(), byDomain.getId());
        assertThat(byLearnableCourses(noUnit).keySet()).containsExactly(noOwnerRegistered.getId());
        assertThat(byLearnableCourses(locked)).isEmpty();
        assertThat(byLearnableCourses(ownerReader).keySet()).contains(registered.getId()).doesNotContain(noOwner.getId());
        assertThat(byLearnableCourses(assigned)).containsEntry(taught.getId(), true);
    }

    private Lesson lesson(String id, Course course, Chapter chapter, int order, String status) {
        var lesson = Lesson.builder().id(run + id).title(id).course(course).chapter(chapter).lessonOrder(order)
                .status(status).build();
        em.persist(lesson);
        return lesson;
    }

    private Chapter chapter(Course course, String code, int order, String status) {
        var chapter = Chapter.builder().course(course).code(code).title(code).chapterOrder(order).status(status).build();
        em.persist(chapter);
        return chapter;
    }

    @Test void openLessonsMatchRequireLessonForEveryCompletionState() {
        var unit = unit("gate", null, true);
        var domain = TrainingDomain.builder().code(run + "gd").name("D").build();
        em.persist(domain);
        var program = program("gate", domain);
        var flowing = course("flowing", program, unit, "PUBLISHED");
        var first = chapter(flowing, "A", 0, "PUBLISHED");
        var hiddenChapter = chapter(flowing, "H", 1, "DRAFT");
        var second = chapter(flowing, "B", 2, "PUBLISHED");
        var blocked = course("blocked", program, unit, "PUBLISHED");
        var blockedFirst = chapter(blocked, "A", 0, "PUBLISHED");
        var empty = chapter(blocked, "E", 1, "PUBLISHED");
        var afterEmpty = chapter(blocked, "C", 2, "PUBLISHED");
        var lessons = List.of(lesson("l1", flowing, first, 0, "PUBLISHED"), lesson("l2", flowing, first, 1, "PUBLISHED"),
                lesson("draft", flowing, first, 2, "DRAFT"), lesson("hidden", flowing, hiddenChapter, 0, "PUBLISHED"),
                lesson("l3", flowing, second, 0, "PUBLISHED"), lesson("b1", blocked, blockedFirst, 0, "PUBLISHED"),
                lesson("b2", blocked, afterEmpty, 0, "PUBLISHED"));
        var learner = user("gateLearner", unit, "ROLE_USER");
        learner.getRegisteredCourses().addAll(List.of(flowing, blocked));
        em.flush();

        for (int mask = 0; mask < 1 << lessons.size(); mask++) {
            learner.getCompletedLessons().clear();
            for (int i = 0; i < lessons.size(); i++)
                if ((mask & 1 << i) != 0)
                    learner.getCompletedLessons().add(lessons.get(i));
            em.flush();
            Set<String> expected = new HashSet<>();
            for (Lesson lesson : lessons) {
                try {
                    access.requireLesson(learner, lesson.getId());
                    expected.add(lesson.getId());
                } catch (AccessDeniedException denied) {
                    // Locked.
                }
            }
            assertThat(new HashSet<>(learning.openLessonIds(lessons.stream().map(Lesson::getId).toList(), learner.getUsername()))).as("completed mask %s", mask)
                    .isEqualTo(expected);
        }
    }

    private record Deck(User learner, Lesson lesson) {
    }

    private Deck deck(String name, int cards, int unrelatedCourses) {
        var unit = unit(name, null, true);
        var domain = TrainingDomain.builder().code(run + name).name("D").build();
        em.persist(domain);
        var program = program(name, domain);
        var course = course(name, program, unit, "PUBLISHED");
        var lesson = lesson(name + "L", course, chapter(course, "A", 0, "PUBLISHED"), 0, "PUBLISHED");
        var now = LocalDateTime.now();
        for (int i = 0; i < cards; i++)
            em.persist(Flashcard.builder().course(course).lesson(lesson).status("PUBLISHED").front("F").back("B")
                    .cardOrder(i).createdAt(now).updatedAt(now).build());
        var other = unit(name + "o", null, true);
        for (int i = 0; i < unrelatedCourses; i++) {
            var unrelated = course(name + "u" + i, program(name + "u" + i, domain), other, "PUBLISHED");
            lesson(name + "u" + i + "L", unrelated, chapter(unrelated, "A", 0, "PUBLISHED"), 0, "PUBLISHED");
        }
        var learner = user(name + "U", unit, "ROLE_USER");
        learner.getRegisteredCourses().add(course);
        em.flush();
        em.clear();
        return new Deck(learner, lesson);
    }

    private Statistics stats() {
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }

    private long sessionStatements(Deck deck, int expectedCards) {
        var today = LocalDate.now(com.bidv.coretraining.policy.FlashcardPlanSchedule.ZONE);
        var plan = plans.create(new FlashcardPlanDto.Request("P", today, today.plusDays(7), List.of(1, 2, 3, 4, 5, 6, 7),
                LocalTime.of(20, 0), true, List.of(new FlashcardPlanDto.Item(deck.lesson().getId(), null)), null),
                deck.learner().getUsername());
        em.flush();
        em.clear();
        var stats = stats();
        assertThat(plans.session(plan.id(), deck.learner().getUsername()).cards()).hasSize(expectedCards);
        return stats.getPrepareStatementCount();
    }

    @Test void sessionStatementsDoNotGrowWithItsCards() {
        long few = sessionStatements(deck("few", 2, 0), 2);
        long many = sessionStatements(deck("many", 40, 0), 40);
        System.out.printf("FLASHCARD_SESSION_AUDIT cards=2 statements=%d cards=40 statements=%d%n", few, many);
        assertThat(many).isEqualTo(few);
    }

    @Test void libraryLoadsOnlyTheLearnersCourses() {
        var small = deck("small", 1, 0);
        var stats = stats();
        learning.library(null, 30, small.learner().getUsername());
        long baseStatements = stats.getPrepareStatementCount();
        long baseEntities = stats.getEntityLoadCount();
        em.clear();
        var crowded = deck("crowded", 1, 100);
        stats = stats();
        assertThat(learning.library(null, 30, crowded.learner().getUsername()).courses()).hasSize(1);
        System.out.printf("FLASHCARD_LIBRARY_SCOPE base statements=%d entities=%d with100More statements=%d entities=%d%n",
                baseStatements, baseEntities, stats.getPrepareStatementCount(), stats.getEntityLoadCount());
        assertThat(stats.getPrepareStatementCount()).isEqualTo(baseStatements);
        assertThat(stats.getEntityLoadCount()).isEqualTo(baseEntities);
    }
}
