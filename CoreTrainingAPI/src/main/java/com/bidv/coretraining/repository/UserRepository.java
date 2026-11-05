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
        @Query("select count(l) > 0 from User u join u.completedLessons l where u.id=:userId and l.id=:lessonId")
        boolean hasCompletedLesson(@Param("userId") Long userId, @Param("lessonId") String lessonId);
        @Query("select u.id from User u join u.assignedRoles r where r.id=:roleId")
        List<Long> findIdsByAssignedRole(@Param("roleId") String roleId);

        @Query("select l.id from User u join u.completedLessons l where u.id=:userId and l.course.id=:courseId")
        java.util.Set<String> findCompletedLessonIds(@Param("userId") Long userId, @Param("courseId") String courseId);

        // Serializes progress creation and monotonic updates across tabs/devices without changing other users.
        @Query(value="select id from users where id=:id for update", nativeQuery=true)
        Optional<Long> lockForLearning(@Param("id") Long id);

        @Query("select count(u) > 0 from User u join u.completedLessons l where l.id in :lessonIds")
        boolean anyCompletedLessonIn(@Param("lessonIds") java.util.Collection<String> lessonIds);
        @Query("select count(u) > 0 from User u join u.quizScores s where s.lesson.id in :lessonIds")
        boolean anyQuizScoreForLessonIn(@Param("lessonIds") java.util.Collection<String> lessonIds);
        Optional<User> findByUsername(String username);

        boolean existsByUsername(String username);

        List<User> findByRole(String role);

        List<User> findByOrganizationUnitIdIn(java.util.Collection<String> ids);

        @Query("select count(course) > 0 from User user join user.registeredCourses course " +
                        "where user.username = :username and course.id = :courseId")
        boolean hasRegisteredCourse(@Param("username") String username, @Param("courseId") String courseId);

        @Query("select count(course) > 0 from User user join user.userGroups userGroup " +
                        "join userGroup.mandatoryCourses course where user.username = :username and course.id = :courseId")
        boolean hasMandatoryCourse(@Param("username") String username, @Param("courseId") String courseId);

        @Query("select lesson.id from User user join user.completedLessons lesson "
                        + "where user.id = :userId and lesson.course.id in :courseIds")
        java.util.Set<String> findCompletedLessonIdsInCourses(@Param("userId") Long userId,
                        @Param("courseIds") java.util.Collection<String> courseIds);

        long countByUserGroupsId(String groupId);

        boolean existsByUserGroupsId(String groupId);

        @Query("""
                select u from User u where (u.organizationUnit.id in :eligibleUnitIds or
                  ((u.organizationUnit.id in :scopeUnitIds or (:includeUnassigned = true and u.organizationUnit is null))
                  and exists (select g.id from User member join member.userGroups g
                    where member.id = u.id and g.id = :groupId)))
                and (:membersOnly = false or exists
                  (select g.id from User member join member.userGroups g where member.id = u.id and g.id = :groupId))
                and (lower(u.fullName) like :query or lower(u.username) like :query
                  or lower(u.employeeId) like :query or lower(u.email) like :query)
                """)
        org.springframework.data.domain.Page<User> findGroupCandidates(
                @Param("eligibleUnitIds") java.util.Collection<String> eligibleUnitIds,
                @Param("scopeUnitIds") java.util.Collection<String> scopeUnitIds,
                @Param("includeUnassigned") boolean includeUnassigned, @Param("groupId") String groupId,
                @Param("membersOnly") boolean membersOnly, @Param("query") String query,
                org.springframework.data.domain.Pageable pageable);
}
