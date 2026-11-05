package com.bidv.coretraining.dto;

import com.bidv.coretraining.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.Instant;

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
    /** Serialized as ISO-8601 UTC so clients never read it in their own timezone by accident. */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
