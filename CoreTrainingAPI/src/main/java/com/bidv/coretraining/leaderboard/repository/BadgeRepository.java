package com.bidv.coretraining.leaderboard.repository;

import com.bidv.coretraining.leaderboard.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, String> {
    List<Badge> findByCourseId(String courseId);
}
