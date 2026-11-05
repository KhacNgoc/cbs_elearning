package com.bidv.coretraining.service;
import com.bidv.coretraining.enums.NotificationType;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.NotificationRepository;
import com.bidv.coretraining.service.impl.NotificationServiceImpl;
import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql=false, properties={"spring.config.location=classpath:access-test.yml", "spring.sql.init.mode=never"})
@ContextConfiguration(classes=com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({ NotificationServiceImpl.class, com.bidv.coretraining.repository.impl.NotificationNativeRepositoryImpl.class })
class NotificationPaginationTest {
    @Autowired EntityManager em;
    @Autowired NotificationService service;
    @Autowired NotificationRepository repository;
    @MockBean NotificationWebSocketHandler socket;
    User user() {
        var u = User.builder().username(UUID.randomUUID().toString()).password("test").fullName("Test").role("ROLE_USER").build();
        em.persist(u); return u;
    }
    Notification add(User user, LocalDateTime time) {
        var n = Notification.builder().recipient(user).type(NotificationType.COMMENT_REPLY).title("Title")
            .message("Message").createdAt(time).build();
        em.persist(n); return n;
    }
    @Test void sixAtATimeWithoutGapsWhenNewNotificationsArrive() {
        var u = user(); var other = user(); var time = LocalDateTime.of(2026, 10, 23, 10, 0);
        var expected = new HashSet<Long>();
        for (int i=0; i<14; i++) expected.add(add(u, time).getId());
        add(other, time.plusDays(1)); em.flush(); em.clear();
        var first = service.getPageForUser(u.getUsername(), null, 6);
        assertThat(first.content()).hasSize(6); assertThat(first.unreadCount()).isEqualTo(14);
        var seen = new ArrayList<Long>(first.content().stream().map(n -> n.getId()).toList());
        add(em.find(User.class, u.getId()), time.plusDays(2)); em.flush();
        var next = service.getPageForUser(u.getUsername(), first.nextBeforeId(), 6);
        assertThat(next.content()).hasSize(6); assertThat(next.hasMore()).isTrue();
        seen.addAll(next.content().stream().map(n -> n.getId()).toList());
        var last = service.getPageForUser(u.getUsername(), next.nextBeforeId(), 6);
        seen.addAll(last.content().stream().map(n -> n.getId()).toList());
        assertThat(last.content()).hasSize(2); assertThat(last.hasMore()).isFalse();
        assertThat(last.nextBeforeId()).isNull(); assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }
    @Test void markAllReadIncludesUnloadedNotificationsBeyondFifty() {
        var u = user(); var other = user();
        for (int i=0; i<61; i++) add(u, LocalDateTime.now());
        add(other, LocalDateTime.now()); em.flush(); em.clear();
        assertThat(service.getPageForUser(u.getUsername(), null, 6).unreadCount()).isEqualTo(61);
        service.markAllRead(u.getUsername()); em.flush(); em.clear();
        assertThat(repository.countByRecipientUsernameAndReadFalse(u.getUsername())).isZero();
        assertThat(repository.countByRecipientUsernameAndReadFalse(other.getUsername())).isEqualTo(1);
        assertThat(service.getPageForUser(u.getUsername(), null, 1000).content()).hasSize(50).allMatch(n -> n.isRead());
    }
    @Test void createdAtIsUtcWhateverTheJvmTimezoneAndSerializesWithAnOffset() throws Exception {
        var previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            var u = user(); var before = java.time.Instant.now();
            var created = service.create(u.getUsername(), NotificationType.COMMENT_REPLY, "Title", "Message", null);
            assertThat(created.getCreatedAt()).isBetween(before, java.time.Instant.now());
            em.flush(); em.clear();
            assertThat(repository.findById(created.getId()).orElseThrow().getCreatedAt().toInstant(java.time.ZoneOffset.UTC))
                .isBetween(before.minusSeconds(1), java.time.Instant.now());
            var stored = add(u, LocalDateTime.of(2026, 11, 4, 16, 58, 12)); em.flush(); em.clear();
            var dto = service.getPageForUser(u.getUsername(), null, 6).content().stream()
                .filter(n -> n.getId().equals(stored.getId())).findFirst().orElseThrow();
            var json = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build().writeValueAsString(dto);
            assertThat(json).contains("\"createdAt\":\"2026-11-04T16:58:12Z\"");
        } finally { TimeZone.setDefault(previous); }
    }
    @Test void emptyAndForeignCursorAreHandled() {
        var u = user(); var foreign = add(user(), LocalDateTime.now()); em.flush();
        assertThat(service.getPageForUser(u.getUsername(), null, 6).content()).isEmpty();
        assertThatThrownBy(() -> service.getPageForUser(u.getUsername(), foreign.getId(), 6))
            .isInstanceOf(com.bidv.coretraining.exception.ResourceNotFoundException.class);
    }
}
