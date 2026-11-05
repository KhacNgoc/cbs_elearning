package com.bidv.coretraining.leaderboard.dto;

import java.util.*;

/** Data contracts for LeaderboardReadService. */
public final class LeaderboardReadDto {
    private LeaderboardReadDto() {}

    public record Page(List<LeaderboardUserDto> items, long total, LeaderboardUserDto currentUser) {}
}
