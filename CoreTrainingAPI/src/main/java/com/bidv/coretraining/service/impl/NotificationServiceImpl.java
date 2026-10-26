package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.NotificationDto;
import com.bidv.coretraining.entity.Notification;
import com.bidv.coretraining.entity.NotificationType;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.NotificationRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.NotificationService;
import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final NotificationWebSocketHandler socketHandler;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public NotificationServiceImpl(NotificationRepository repository,
            UserRepository userRepository,
            NotificationWebSocketHandler socketHandler) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.socketHandler = socketHandler;
    }

    @Override
    @Transactional
    public NotificationDto create(String username, NotificationType type, String title,
            String message, String targetUrl) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Notification saved = repository.save(Notification.builder()
                .recipient(user).type(type).title(title).message(message)
                .targetUrl(targetUrl).createdAt(LocalDateTime.now()).build());
        NotificationDto dto = toDto(saved);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            socketHandler.sendToUser(username, dto);
                        }
                    });
        } else
            socketHandler.sendToUser(username, dto);
        return dto;
    }

    @Override
    @Transactional
    public void createForAdmins(NotificationType type, String title, String message, String targetUrl) {
        userRepository.findAll().stream().filter(com.bidv.coretraining.security.AccessPolicy::system)
                .forEach(admin -> create(admin.getUsername(), type, title, message, targetUrl));
    }

    @Override
    @Transactional
    public void createForUnitAdmins(com.bidv.coretraining.entity.OrganizationUnit unit, NotificationType type,
            String title, String message, String targetUrl) {
        userRepository.findAll().stream()
                .filter(u -> u.activeAccount() && (com.bidv.coretraining.security.AccessPolicy.system(u)
                        || com.bidv.coretraining.security.AccessPolicy.allows(u, "enrollments:update", unit)))
                .forEach(u -> create(u.getUsername(), type, title, message, targetUrl));
    }

    @Override
    @Transactional(readOnly = true)
    public int countCompletedLearners(String lessonId) {
        if (lessonId == null || lessonId.isBlank()) return 0;
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM user_completed_lessons WHERE lesson_id = :lessonId",
                Map.of("lessonId", lessonId),
                Integer.class);
        return count != null ? count : 0;
    }

    @Override
    @Transactional
    public int notifyLessonUpdateToCompletedLearners(String lessonId, String title, String message, String targetUrl) {
        if (lessonId == null || lessonId.isBlank()) return 0;

        Map<String, Object> params = Map.of(
                "lessonId", lessonId,
                "title", title,
                "message", message,
                "targetUrl", targetUrl != null ? targetUrl : ""
        );

        // 1. Bulk insert notifications for users who completed this lesson
        String insertSql = """
            INSERT INTO notifications (recipient_id, type, title, message, target_url, is_read, created_at)
            SELECT DISTINCT
                ucl.user_id,
                'COURSE_CONTENT_UPDATED',
                :title,
                :message,
                :targetUrl,
                false,
                NOW()
            FROM user_completed_lessons ucl
            WHERE ucl.lesson_id = :lessonId
            """;
        int inserted = namedParameterJdbcTemplate.update(insertSql, params);
        if (inserted <= 0) return 0;

        // 2. Fetch usernames of online users to push via WebSocket
        List<String> usernames = namedParameterJdbcTemplate.queryForList(
                "SELECT DISTINCT u.username FROM users u JOIN user_completed_lessons ucl ON u.id = ucl.user_id WHERE ucl.lesson_id = :lessonId",
                params, String.class);

        NotificationDto dto = NotificationDto.builder()
                .type(NotificationType.COURSE_CONTENT_UPDATED)
                .title(title)
                .message(message)
                .targetUrl(targetUrl)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            for (String uname : usernames) {
                                socketHandler.sendToUser(uname, dto);
                            }
                        }
                    });
        } else {
            for (String uname : usernames) {
                socketHandler.sendToUser(uname, dto);
            }
        }
        return inserted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getForUser(String username) {
        return repository.findTop50ByRecipientUsernameOrderByCreatedAtDesc(username)
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public NotificationDto markRead(Long id, String username) {
        Notification notification = repository.findByIdAndRecipientUsername(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        notification.setRead(true);
        return toDto(repository.save(notification));
    }

    @Override
    @Transactional
    public void markAllRead(String username) {
        repository.findTop50ByRecipientUsernameOrderByCreatedAtDesc(username).stream()
                .filter(notification -> !notification.isRead())
                .forEach(notification -> notification.setRead(true));
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId()).type(notification.getType())
                .title(notification.getTitle()).message(notification.getMessage())
                .targetUrl(notification.getTargetUrl()).read(notification.isRead())
                .createdAt(notification.getCreatedAt()).build();
    }
}
