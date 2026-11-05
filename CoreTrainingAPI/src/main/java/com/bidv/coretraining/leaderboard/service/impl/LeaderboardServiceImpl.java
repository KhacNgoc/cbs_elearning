package com.bidv.coretraining.leaderboard.service.impl;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.leaderboard.dto.LeaderboardUserDto;
import com.bidv.coretraining.leaderboard.service.LeaderboardReadService;
import com.bidv.coretraining.leaderboard.service.LeaderboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {
 private final LeaderboardReadService reads;
 private final AuthorizationService authorization;
 public LeaderboardServiceImpl(LeaderboardReadService reads,AuthorizationService authorization){this.reads=reads;this.authorization=authorization;}
 /** Compatibility endpoint is bounded; the UI uses the paged contract. */
 @Override @Transactional(readOnly=true)
 public List<LeaderboardUserDto> getLeaderboard(){return reads.page(authorization.current(),0,100).items();}
}
