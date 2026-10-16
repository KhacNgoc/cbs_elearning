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

        @Query("select count(course) > 0 from User user join user.userGroups userGroup " +
                        "join userGroup.mandatoryCourses course where user.username = :username and course.id = :courseId")
        boolean hasMandatoryCourse(@Param("username") String username, @Param("courseId") String courseId);

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
