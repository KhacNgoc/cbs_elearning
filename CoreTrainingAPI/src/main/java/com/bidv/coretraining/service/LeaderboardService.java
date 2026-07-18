package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LeaderboardUserDto;

import java.util.List;

public interface LeaderboardService {
    List<LeaderboardUserDto> getLeaderboard();
}
