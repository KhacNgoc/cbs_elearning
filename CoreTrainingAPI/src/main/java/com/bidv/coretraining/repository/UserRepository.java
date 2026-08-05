package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRole(String role);

    @Query("select count(topic) > 0 from User user join user.registeredTopics topic " +
            "where user.username = :username and topic.id = :topicId")
    boolean hasRegisteredTopic(@Param("username") String username, @Param("topicId") String topicId);

    @Query("select count(topic) > 0 from User user join user.userGroup userGroup " +
            "join userGroup.mandatoryTopics topic where user.username = :username and topic.id = :topicId")
    boolean hasMandatoryTopic(@Param("username") String username, @Param("topicId") String topicId);
}
