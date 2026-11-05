package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.dto.FlashcardPlanDto;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.enums.NotificationType;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.service.impl.FlashcardLearningServiceImpl;
import com.bidv.coretraining.service.impl.FlashcardPlanServiceImpl;
import com.bidv.coretraining.service.impl.LearningAccessServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Runs against a real schema: the library must not issue queries per course, and plans must reach real cards. */
@DataJpaTest(showSql = false, properties = { "spring.config.location=classpath:access-test.yml",
        "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.generate_statistics=true" })
@ContextConfiguration(classes = com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({ LearningAccessServiceImpl.class, FlashcardLearningServiceImpl.class, FlashcardPlanServiceImpl.class,
        com.bidv.coretraining.repository.impl.FlashcardPlanNativeRepositoryImpl.class,
        com.bidv.coretraining.repository.impl.LearnerDashboardQueryRepositoryImpl.class,
        com.bidv.coretraining.repository.support.LearnerDashboardSqlRelations.class })
class FlashcardPlanQueryAuditTest {
    @Autowired FlashcardLearningService learning;
    @Autowired FlashcardPlanService plans;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @MockBean NotificationService notifications;

    private record Seeded(User user, List<Lesson> first, List<Lesson> second) {
    }

    private Seeded seed(int courses) {
        String id = UUID.randomUUID().toString();
        var unit = OrganizationUnit.builder().id(id).name("Unit").build();
        em.persist(unit);
        var user = User.builder().username("u" + id).fullName("Learner").password("x").role("ROLE_USER").organizationUnit(unit)
                .email(id + "@test.local").build();
        em.persist(user);
        List<Lesson> first = new java.util.ArrayList<>();
        List<Lesson> second = new java.util.ArrayList<>();
        for (int c = 0; c < courses; c++) {
            String key = id + "-" + c;
            // Each course has its own program and domain, the shape that used to cost queries per course.
            var domain = TrainingDomain.builder().code(key).name("Domain").build();
            em.persist(domain);
            var program = TrainingProgram.builder().code(key).name("Program").domain(domain).build();
            em.persist(program);
            var course = Course.builder().id(key).code(key).name("Course " + c).ownerUnit(unit).program(program)
                    .status("PUBLISHED").build();
            em.persist(course);
            var chapter = Chapter.builder().course(course).code("CH").title("Chapter").chapterOrder(0)
                    .status("PUBLISHED").build();
            em.persist(chapter);
            for (int l = 0; l < 2; l++) {
                var lesson = Lesson.builder().id(key + "-" + l).title("Lesson " + l).course(course).chapter(chapter)
                        .lessonOrder(l).status("PUBLISHED").build();
                em.persist(lesson);
                (l == 0 ? first : second).add(lesson);
                var now = LocalDateTime.now();
                em.persist(Flashcard.builder().course(course).lesson(lesson).status("PUBLISHED").front("F" + l)
                        .back("B").cardOrder(0).createdAt(now).updatedAt(now).build());
            }
            user.getRegisteredCourses().add(course);
        }
        em.flush();
        em.clear();
        return new Seeded(em.find(User.class, user.getId()), first, second);
    }

    private long libraryStatements(String username) {
        em.flush();
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        learning.library(null, 50, username);
        return stats.getPrepareStatementCount();
    }

    @Test void libraryQueriesDoNotGrowWithTheNumberOfCourses() {
        long few = libraryStatements(seed(2).user().getUsername());
        long many = libraryStatements(seed(30).user().getUsername());
        System.out.printf("FLASHCARD_LIBRARY_AUDIT courses=2 statements=%d courses=30 statements=%d%n", few, many);
        // Courses from the first seed are published too, so "many" reads 32 courses against "few" with 2.
        assertThat(many).isLessThanOrEqualTo(few + 10);
    }

    @Test void libraryKeepsTheLessonGateAndEnrollment() {
        var seeded = seed(1);
        var other = seed(1);
        var library = learning.library(null, 30, seeded.user().getUsername());
        assertThat(library.courses()).hasSize(1);
        assertThat(library.courses().get(0).lessons()).extracting(FlashcardDto.LibraryLesson::id)
                .containsExactly(seeded.first().get(0).getId());
        assertThat(library.courses().get(0).id()).isNotEqualTo(other.first().get(0).getCourse().getId());
    }

    private FlashcardPlanDto.Request request(String lessonId, LocalTime time) {
        LocalDate today = LocalDate.now(com.bidv.coretraining.policy.FlashcardPlanSchedule.ZONE);
        return new FlashcardPlanDto.Request("Ôn tối", today, today.plusDays(7), List.of(1, 2, 3, 4, 5, 6, 7), time, true,
                List.of(new FlashcardPlanDto.Item(lessonId, null)), null);
    }

    @Test void planSessionReturnsOnlyOpenLessonCards() {
        var seeded = seed(1);
        String username = seeded.user().getUsername();
        var plan = plans.create(request(seeded.first().get(0).getId(), LocalTime.of(20, 0)), username);
        assertThat(plan.nextReminderAt()).isNotNull();
        assertThat(plans.session(plan.id(), username).cards()).hasSize(1);
        assertThat(plans.list(username)).hasSize(1);
    }

    @Test void plansCannotReachLockedLessonsOrOtherLearners() {
        var seeded = seed(1);
        var other = seed(1);
        assertThatThrownBy(() -> plans.create(request(seeded.second().get(0).getId(), null), seeded.user().getUsername()))
                .isInstanceOf(BadRequestException.class);
        var plan = plans.create(request(seeded.first().get(0).getId(), null), seeded.user().getUsername());
        assertThatThrownBy(() -> plans.session(plan.id(), other.user().getUsername()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void makeDue(Long id, Instant at) {
        em.flush();
        em.createQuery("update FlashcardStudyPlan p set p.nextReminderAt = :at where p.id = :id")
                .setParameter("at", at).setParameter("id", id).executeUpdate();
        em.clear();
    }

    @Test void dueReminderIsSentOnceAndMovesOn() {
        var seeded = seed(1);
        String username = seeded.user().getUsername();
        var plan = plans.create(request(seeded.first().get(0).getId(), LocalTime.of(20, 0)), username);
        Instant now = Instant.now();
        makeDue(plan.id(), now.minus(1, ChronoUnit.MINUTES));

        assertThat(plans.sendDueReminders(now, 200)).isEqualTo(1);
        em.flush();
        verify(notifications).createAll(List.of(new com.bidv.coretraining.dto.NotificationDraft(seeded.user().getId(),
                username, NotificationType.FLASHCARD_STUDY_REMINDER, "Đến giờ học flashcard",
                "Lịch \"Ôn tối\" đã đến giờ học.", "/flashcards?plan=" + plan.id())));
        em.clear();
        assertThat(em.find(FlashcardStudyPlan.class, plan.id()).getNextReminderAt()).isAfter(now);
        assertThat(plans.sendDueReminders(now, 200)).isZero();
    }

    @Test void aReminderHoursLateIsDroppedButTheScheduleContinues() {
        var seeded = seed(1);
        var plan = plans.create(request(seeded.first().get(0).getId(), LocalTime.of(20, 0)), seeded.user().getUsername());
        Instant now = Instant.now();
        makeDue(plan.id(), now.minus(3, ChronoUnit.HOURS));
        assertThat(plans.sendDueReminders(now, 200)).isEqualTo(1);
        em.flush();
        verify(notifications).createAll(List.of());
        em.clear();
        assertThat(em.find(FlashcardStudyPlan.class, plan.id()).getNextReminderAt()).isAfter(now);
    }

    @Test void pausingStopsRemindersWithoutConflictingWithTheJob() {
        var seeded = seed(1);
        String username = seeded.user().getUsername();
        String lesson = seeded.first().get(0).getId();
        var plan = plans.create(request(lesson, LocalTime.of(20, 0)), username);
        Instant now = Instant.now();
        makeDue(plan.id(), now.minus(1, ChronoUnit.MINUTES));
        plans.sendDueReminders(now, 200);
        em.flush();
        em.clear();
        // The learner's copy predates the reminder and must still save.
        var base = request(lesson, LocalTime.of(20, 0));
        var paused = plans.update(plan.id(), new FlashcardPlanDto.Request(base.name(), base.startDate(), base.endDate(),
                base.weekdays(), base.reminderTime(), false, null, plan.version()), username);
        assertThat(paused.active()).isFalse();
        assertThat(paused.nextReminderAt()).isNull();
        assertThat(paused.items()).hasSize(1);
    }
}
