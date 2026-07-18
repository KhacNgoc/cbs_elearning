package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBadgeDto {
    private String id;
    private String title;
    private String type; // bronze, silver, gold
    private String topicId;
    private String topicName;
    private String unlockedAt; // YYYY-MM-DD
    private String icon;
}
