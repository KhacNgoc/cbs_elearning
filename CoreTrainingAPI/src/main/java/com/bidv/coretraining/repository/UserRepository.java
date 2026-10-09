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

    @Query("select count(course) > 0 from User user join user.registeredCourses course " +
            "where user.username = :username and course.id = :courseId")
    boolean hasRegisteredCourse(@Param("username") String username, @Param("courseId") String courseId);

    @Query("select count(course) > 0 from User user join user.userGroup userGroup " +
            "join userGroup.mandatoryCourses course where user.username = :username and course.id = :courseId")
    boolean hasMandatoryCourse(@Param("username") String username, @Param("courseId") String courseId);
}
