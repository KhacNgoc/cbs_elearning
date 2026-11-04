package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.NotificationDto;
import com.bidv.coretraining.enums.NotificationType;

import java.util.List;

public interface NotificationService {
    NotificationDto create(String username, NotificationType type, String title, String message, String targetUrl);

    void createForAdmins(NotificationType type, String title, String message, String targetUrl);

    void createForUnitAdmins(com.bidv.coretraining.entity.OrganizationUnit unit, NotificationType type, String title,
            String message, String targetUrl);

    int notifyLessonUpdateToCompletedLearners(String lessonId, String title, String message, String targetUrl);

    int countCompletedLearners(String lessonId);

    List<NotificationDto> getForUser(String username);
    record NotificationPage(List<NotificationDto> content, Long nextBeforeId, boolean hasMore, long unreadCount) {}
    NotificationPage getPageForUser(String username, Long beforeId, int size);

    NotificationDto markRead(Long id, String username);

    void markAllRead(String username);
}
