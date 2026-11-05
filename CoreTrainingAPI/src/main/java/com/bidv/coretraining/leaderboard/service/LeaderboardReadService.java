package com.bidv.coretraining.leaderboard.service;

import com.bidv.coretraining.leaderboard.dto.LeaderboardReadDto.*;
import com.bidv.coretraining.user.entity.User;
import java.util.*;

/** Business operations for LeaderboardReadService. */
public interface LeaderboardReadService {
    public Page page(User actor,int page,int size);
}
