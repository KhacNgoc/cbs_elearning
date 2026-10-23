package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.NotificationDto;
import com.bidv.coretraining.entity.Notification;
import com.bidv.coretraining.entity.NotificationType;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.NotificationRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.NotificationService;
import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final NotificationWebSocketHandler socketHandler;

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
    public List<NotificationDto> getForUser(String username) {
        return repository.findTop50ByRecipientUsernameOrderByCreatedAtDesc(username)
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage getPageForUser(String username, Long beforeId, int size) {
        int limit = Math.max(1, Math.min(size, 50));
        Notification before = beforeId == null ? null : repository.findByIdAndRecipientUsername(beforeId, username)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", beforeId));
        var pageable = org.springframework.data.domain.PageRequest.of(0, limit + 1);
        var rows = before == null ? repository.findByRecipientUsernameOrderByCreatedAtDescIdDesc(username, pageable)
            : repository.findOlder(username, before.getCreatedAt(), beforeId, pageable);
        boolean hasMore = rows.size() > limit;
        var content = rows.stream().limit(limit).map(this::toDto).toList();
        return new NotificationPage(content, hasMore ? content.get(content.size() - 1).getId() : null,
            hasMore, repository.countByRecipientUsernameAndReadFalse(username));
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
        repository.markAllRead(username);
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId()).type(notification.getType())
                .title(notification.getTitle()).message(notification.getMessage())
                .targetUrl(notification.getTargetUrl()).read(notification.isRead())
                .createdAt(notification.getCreatedAt()).build();
    }
}
