package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardUserDto {
    private Integer rank;
    private String username;
    private String fullName;
    private Integer points;
    private String avatarColor;
    private Integer badgesCount;
    private Integer completedLessonsCount;
}
