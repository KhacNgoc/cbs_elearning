package com.bidv.coretraining.notification.dto;

import com.bidv.coretraining.enums.NotificationType;

/** A notification to create for a recipient the caller already knows. */
public record NotificationDraft(Long recipientId, String username, NotificationType type, String title,
        String message, String targetUrl) {
}
