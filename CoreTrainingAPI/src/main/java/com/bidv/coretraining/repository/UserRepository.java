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
    @Query("select u.id from User u join u.assignedRoles r where r.id=:roleId")
    List<Long> findIdsByAssignedRole(@Param("roleId") String roleId);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRole(String role);
    List<User> findByOrganizationUnitIdIn(java.util.Collection<String> ids);


    @Query("select count(course) > 0 from User user join user.registeredCourses course " +
            "where user.username = :username and course.id = :courseId")
    boolean hasRegisteredCourse(@Param("username") String username, @Param("courseId") String courseId);

    @Query("select count(course) > 0 from User user join user.userGroup userGroup " +
            "join userGroup.mandatoryCourses course where user.username = :username and course.id = :courseId")
    boolean hasMandatoryCourse(@Param("username") String username, @Param("courseId") String courseId);
}
