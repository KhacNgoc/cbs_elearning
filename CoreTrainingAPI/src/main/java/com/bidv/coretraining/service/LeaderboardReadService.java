package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LeaderboardReadDto.*;
import com.bidv.coretraining.entity.User;
import java.util.*;

/** Business operations for LeaderboardReadService. */
public interface LeaderboardReadService {
    public Page page(User actor,int page,int size);
}
