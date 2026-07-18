package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.UserQuizScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserQuizScoreRepository extends JpaRepository<UserQuizScore, Long> {
    List<UserQuizScore> findByUserId(Long userId);
    Optional<UserQuizScore> findByUserIdAndTopicId(Long userId, String topicId);
}
