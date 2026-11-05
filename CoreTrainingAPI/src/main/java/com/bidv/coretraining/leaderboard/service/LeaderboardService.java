package com.bidv.coretraining.leaderboard.service;

import com.bidv.coretraining.leaderboard.dto.LeaderboardUserDto;

import java.util.List;

public interface LeaderboardService {
    List<LeaderboardUserDto> getLeaderboard();
}
