package com.bidv.coretraining.flashcard.service;

import com.bidv.coretraining.enums.NotificationType;
import com.bidv.coretraining.flashcard.entity.FlashcardStudyPlan;
import com.bidv.coretraining.flashcard.service.impl.FlashcardLearningServiceImpl;
import com.bidv.coretraining.flashcard.service.impl.FlashcardPlanServiceImpl;
import com.bidv.coretraining.learner.service.impl.LearningAccessServiceImpl;
import com.bidv.coretraining.notification.dto.NotificationDraft;
import com.bidv.coretraining.notification.dto.NotificationDto;
import com.bidv.coretraining.notification.entity.Notification;
import com.bidv.coretraining.notification.repository.NotificationNativeRepository;
import com.bidv.coretraining.notification.repository.NotificationRepository;
import com.bidv.coretraining.notification.service.impl.NotificationServiceImpl;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Reminders use the real notification service: its batch insert must really reach the table. */
@DataJpaTest(showSql = false, properties = { "spring.config.location=classpath:access-test.yml",
        "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.generate_statistics=true" })
@ContextConfiguration(classes = com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({ LearningAccessServiceImpl.class, FlashcardLearningServiceImpl.class, FlashcardPlanServiceImpl.class,
        NotificationServiceImpl.class, com.bidv.coretraining.notification.repository.impl.NotificationNativeRepositoryImpl.class,
        com.bidv.coretraining.flashcard.repository.impl.FlashcardPlanNativeRepositoryImpl.class,
        com.bidv.coretraining.learner.repository.impl.LearnerDashboardQueryRepositoryImpl.class,
        com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations.class })
class FlashcardReminderBatchTest {
    @Autowired FlashcardPlanService plans;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @MockBean NotificationWebSocketHandler socket;

    private FlashcardStudyPlan duePlan(String status, Instant due, LocalDate end) {
        String id = UUID.randomUUID().toString();
        var user = User.builder().username("u" + id).fullName("L").password("x").role("ROLE_USER").status(status)
                .email(id + "@test.local").build();
        em.persist(user);
        var today = LocalDate.now(com.bidv.coretraining.policy.FlashcardPlanSchedule.ZONE);
        var plan = FlashcardStudyPlan.builder().user(user).name("Ôn").startDate(today).endDate(end).weekdayMask(127)
                .reminderTime(LocalTime.of(20, 0)).active(true).selection("[]").createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now()).nextReminderAt(due).build();
        em.persist(plan);
        return plan;
    }

    private long statementsToSend(int count) {
        Instant now = Instant.now();
        var end = LocalDate.now(com.bidv.coretraining.policy.FlashcardPlanSchedule.ZONE).plusDays(7);
        for (int i = 0; i < count; i++)
            duePlan("ACTIVE", now.minusSeconds(5), end);
        em.flush();
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertThat(plans.sendDueReminders(now, 200)).isEqualTo(count);
        return stats.getPrepareStatementCount();
    }

    @Test void hibernateStatementsDoNotGrowWithRecipients() {
        long few = statementsToSend(2);
        long many = statementsToSend(40);
        System.out.printf("FLASHCARD_REMINDER_AUDIT plans=2 statements=%d plans=40 statements=%d%n", few, many);
        assertThat(many).isEqualTo(few);
    }

    @Test void sendsToActiveRecipientsAndAdvancesEveryPlan() {
        Instant now = Instant.now();
        var today = LocalDate.now(com.bidv.coretraining.policy.FlashcardPlanSchedule.ZONE);
        var active = duePlan("ACTIVE", now.minusSeconds(5), today.plusDays(7));
        var locked = duePlan("LOCKED", now.minusSeconds(5), today.plusDays(7));
        var late = duePlan("ACTIVE", now.minusSeconds(3 * 3600), today.plusDays(7));
        var finished = duePlan("ACTIVE", now.minusSeconds(5), today.minusDays(1));
        em.flush();
        em.clear();

        assertThat(plans.sendDueReminders(now, 200)).isEqualTo(4);
        em.flush();
        em.clear();

        var rows = em.createQuery("select n from Notification n join fetch n.recipient", Notification.class).getResultList();
        assertThat(rows).extracting(n -> n.getRecipient().getId())
                .contains(active.getUser().getId(), finished.getUser().getId())
                .doesNotContain(locked.getUser().getId(), late.getUser().getId());
        var sent = rows.stream().filter(n -> n.getRecipient().getId().equals(active.getUser().getId())).findFirst().orElseThrow();
        assertThat(sent.getType()).isEqualTo(NotificationType.FLASHCARD_STUDY_REMINDER);
        assertThat(sent.getTargetUrl()).isEqualTo("/flashcards?plan=" + active.getId());
        assertThat(sent.isRead()).isFalse();
        assertThat(em.find(FlashcardStudyPlan.class, active.getId()).getNextReminderAt()).isAfter(now);
        assertThat(em.find(FlashcardStudyPlan.class, locked.getId()).getNextReminderAt()).isAfter(now);
        assertThat(em.find(FlashcardStudyPlan.class, late.getId()).getNextReminderAt()).isAfter(now);
        assertThat(em.find(FlashcardStudyPlan.class, finished.getId()).getNextReminderAt()).isNull();
    }

    /** Without an id the bell could neither mark a pushed reminder read nor remove its duplicate on reload. */
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void pushedNotificationsCarryTheirGeneratedIds() {
        var nativeQueries = mock(NotificationNativeRepository.class);
        var pushed = mock(NotificationWebSocketHandler.class);
        var service = new NotificationServiceImpl(mock(NotificationRepository.class), mock(UserRepository.class), pushed,
                nativeQueries);
        var draft = new NotificationDraft(1L, "a", NotificationType.FLASHCARD_STUDY_REMINDER, "T", "M", "/x");
        when(nativeQueries.insertAll(eq(List.of(draft)), any())).thenReturn(List.of(77L));

        service.createAll(List.of(draft));

        var dto = ArgumentCaptor.forClass(NotificationDto.class);
        verify(pushed).sendToUser(eq("a"), dto.capture());
        assertThat(dto.getValue().getId()).isEqualTo(77L);
        assertThat(dto.getValue().getTitle()).isEqualTo("T");
        assertThat(dto.getValue().getCreatedAt()).isNotNull();
    }
}
