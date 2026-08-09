package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {
    private long totalUsers;
    private long activeUsers;
    private long totalLessons;
    private long totalQuestions;
    private List<ActiveSessionDto> activeSessions;
    private List<DailyActivityDto> dailyActivity;
    private Map<String, Integer> competencyDistribution;
    private Map<String, Double> avgScoreByTopic;
}
