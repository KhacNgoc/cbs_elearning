package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, String> {
    List<Badge> findByCourseId(String courseId);
}
