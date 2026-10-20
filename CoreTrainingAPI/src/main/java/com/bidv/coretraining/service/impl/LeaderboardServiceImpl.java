package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.LeaderboardUserDto;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.LeaderboardService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.service.AuthorizationService authorization;
    private final UserRepository userRepository;

    public LeaderboardServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardUserDto> getLeaderboard() {
        // Fetch all users sorted by points descending
        User actor = authorization.current();
        List<User> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "points")).stream()
                .filter(u -> u.activeAccount() && (com.bidv.coretraining.security.AccessPolicy.system(actor)
                        || com.bidv.coretraining.security.AccessPolicy.permissions(actor).contains("overview:read")
                                && com.bidv.coretraining.security.AccessPolicy.allows(actor, "overview:read",
                                        u.getOrganizationUnit())
                        || !com.bidv.coretraining.security.AccessPolicy.permissions(actor).contains("overview:read")
                                && java.util.Objects.equals(
                                        actor.getOrganizationUnit() == null ? null
                                                : actor.getOrganizationUnit().getId(),
                                        u.getOrganizationUnit() == null ? null : u.getOrganizationUnit().getId())))
                .toList();
        List<LeaderboardUserDto> leaderboard = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            // Hide admin from leaderboard
            if ("ROLE_ADMIN".equals(user.getRole())) {
                continue;
            }

            leaderboard.add(LeaderboardUserDto.builder()
                    .rank(leaderboard.size() + 1)
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .points(user.getPoints())
                    .avatarColor(user.getAvatarColor())
                    .badgesCount(user.getUnlockedBadges().size())
                    .completedLessonsCount(user.getCompletedLessons().size())
                    .build());
        }

        return leaderboard;
    }
}
