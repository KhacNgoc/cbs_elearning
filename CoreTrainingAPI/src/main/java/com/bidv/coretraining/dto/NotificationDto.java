package com.bidv.coretraining.dto;

import com.bidv.coretraining.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String targetUrl;
    private boolean read;
    private LocalDateTime createdAt;
}
