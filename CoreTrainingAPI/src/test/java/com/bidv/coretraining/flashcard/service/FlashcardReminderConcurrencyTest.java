package com.bidv.coretraining.flashcard.service;

import com.bidv.coretraining.flashcard.entity.FlashcardStudyPlan;
import com.bidv.coretraining.flashcard.service.impl.FlashcardLearningServiceImpl;
import com.bidv.coretraining.flashcard.service.impl.FlashcardPlanServiceImpl;
import com.bidv.coretraining.learner.service.impl.LearningAccessServiceImpl;
import com.bidv.coretraining.notification.service.impl.NotificationServiceImpl;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Two backends running the reminder job at once, as two transactions on real PostgreSQL row locks. */
@DataJpaTest(showSql = false, properties = { "spring.config.location=classpath:access-test.yml",
        "spring.sql.init.mode=never" })
@ContextConfiguration(classes = com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({ LearningAccessServiceImpl.class, FlashcardLearningServiceImpl.class, FlashcardPlanServiceImpl.class,
        NotificationServiceImpl.class, com.bidv.coretraining.notification.repository.impl.NotificationNativeRepositoryImpl.class,
        com.bidv.coretraining.flashcard.repository.impl.FlashcardPlanNativeRepositoryImpl.class,
        com.bidv.coretraining.learner.repository.impl.LearnerDashboardQueryRepositoryImpl.class,
        com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations.class })
@EnabledIfSystemProperty(named = "cbs.postgres.tests", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlashcardReminderConcurrencyTest {
    @Autowired FlashcardPlanService plans;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockBean NotificationWebSocketHandler socket;

    @Test void concurrentRunsShareDuePlansWithoutSendingAnyTwice() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Instant now = Instant.now();
        List<Long> planIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        tx.executeWithoutResult(status -> {
            var today = LocalDate.now(com.bidv.coretraining.policy.FlashcardPlanSchedule.ZONE);
            for (int i = 0; i < 20; i++) {
                String id = UUID.randomUUID().toString();
                var user = User.builder().username("u" + id).fullName("L").password("x").role("ROLE_USER")
                        .email(id + "@test.local").build();
                em.persist(user);
                var plan = FlashcardStudyPlan.builder().user(user).name("Ôn").startDate(today).endDate(today.plusDays(7))
                        .weekdayMask(127).reminderTime(LocalTime.of(20, 0)).active(true).selection("[]")
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .nextReminderAt(now.minusSeconds(5)).build();
                em.persist(plan);
                em.flush();
                planIds.add(plan.getId());
                userIds.add(user.getId());
            }
        });
        var firstHoldsItsLocks = new CountDownLatch(1);
        var secondFinished = new CountDownLatch(1);
        try {
            // The first run keeps its transaction open, so its rows stay locked while the second one runs.
            var first = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
                int sent = plans.sendDueReminders(now, 12);
                firstHoldsItsLocks.countDown();
                try {
                    assertThat(secondFinished.await(30, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return sent;
            }));
            assertThat(firstHoldsItsLocks.await(30, TimeUnit.SECONDS)).isTrue();
            int second = plans.sendDueReminders(now, 200);
            secondFinished.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(12);
            assertThat(second).isEqualTo(8);
            assertThat(plans.sendDueReminders(now, 200)).isZero();
            tx.executeWithoutResult(status -> assertThat(em.createQuery(
                    "select n.recipient.id, count(n) from Notification n where n.recipient.id in :users group by n.recipient.id",
                    Object[].class).setParameter("users", userIds).getResultList()).hasSize(20)
                    .allSatisfy(row -> assertThat(row[1]).isEqualTo(1L)));
        } finally {
            secondFinished.countDown();
            tx.executeWithoutResult(status -> {
                em.createQuery("delete from Notification n where n.recipient.id in :users")
                        .setParameter("users", userIds).executeUpdate();
                em.createQuery("delete from FlashcardStudyPlan p where p.id in :ids")
                        .setParameter("ids", planIds).executeUpdate();
                em.createQuery("delete from User u where u.id in :users").setParameter("users", userIds).executeUpdate();
            });
        }
    }
}
