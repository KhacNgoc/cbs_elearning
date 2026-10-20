package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.NotificationDto;
import com.bidv.coretraining.entity.NotificationType;

import java.util.List;

public interface NotificationService {
    NotificationDto create(String username, NotificationType type, String title, String message, String targetUrl);

    void createForAdmins(NotificationType type, String title, String message, String targetUrl);

    void createForUnitAdmins(com.bidv.coretraining.entity.OrganizationUnit unit, NotificationType type, String title,
            String message, String targetUrl);

    List<NotificationDto> getForUser(String username);

    NotificationDto markRead(Long id, String username);

    void markAllRead(String username);
}
