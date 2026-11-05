package com.bidv.coretraining.leaderboard.controller;
import com.bidv.coretraining.leaderboard.dto.LeaderboardReadDto;

import com.bidv.coretraining.leaderboard.dto.LeaderboardUserDto;
import com.bidv.coretraining.leaderboard.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final com.bidv.coretraining.leaderboard.service.LeaderboardReadService reads;
    private final com.bidv.coretraining.access.service.AuthorizationService auth;

    public LeaderboardController(LeaderboardService leaderboardService, com.bidv.coretraining.leaderboard.service.LeaderboardReadService reads, com.bidv.coretraining.access.service.AuthorizationService auth) {
        this.reads = reads; this.auth = auth;
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/page")
    public com.bidv.coretraining.leaderboard.dto.LeaderboardReadDto.Page page(@org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page, @org.springframework.web.bind.annotation.RequestParam(defaultValue="25") int size) {
        return reads.page(auth.current(),page,size);
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardUserDto>> getLeaderboard() {
        return ResponseEntity.ok(leaderboardService.getLeaderboard());
    }
}
