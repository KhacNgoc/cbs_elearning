package com.bidv.coretraining.dto;

import com.bidv.coretraining.dto.LeaderboardUserDto;
import java.util.*;

/** Data contracts for LeaderboardReadService. */
public final class LeaderboardReadDto {
    private LeaderboardReadDto() {}

    public record Page(List<LeaderboardUserDto> items, long total, LeaderboardUserDto currentUser) {}
}
