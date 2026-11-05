package com.bidv.coretraining.leaderboard.repository;

import com.bidv.coretraining.leaderboard.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserId(Long userId);

    Optional<UserBadge> findByUserIdAndBadgeId(Long userId, String badgeId);
}
