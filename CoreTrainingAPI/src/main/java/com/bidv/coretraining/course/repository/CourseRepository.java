package com.bidv.coretraining.course.repository;

import com.bidv.coretraining.course.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, String> {
    boolean existsByCode(String code);
    Optional<Course> findByCode(String code);

    @Query("SELECT COALESCE(MAX(c.seqNumber), 0) FROM Course c WHERE c.businessModule = :module")
    Integer findMaxSeqNumberByBusinessModule(@Param("module") String module);

    @Query("select distinct c from Course c left join c.instructorIds i where (:admin = true and c.ownerUnit.id in :units) or (:instructor = true and i = :userId)")
    List<Course> findEditable(
            @Param("units") Collection<String> units,
            @Param("userId") Long userId,
            @Param("admin") boolean admin,
            @Param("instructor") boolean instructor);

    List<Course> findByProgramIdAndStatusNot(String programId, String status);
    List<Course> findByStatus(String status);
    long countByProgramIdAndStatusNot(String programId, String status);

    @Query("SELECT c FROM Course c WHERE c.program.id = :programId AND c.status <> 'DELETED' " +
           "AND (:search IS NULL OR :search = '' OR c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')) " +
           "ORDER BY c.code ASC, c.name ASC")
    Page<Course> findByProgramIdWithFilter(
            @Param("programId") String programId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.status <> 'DELETED' " +
           "AND (:search IS NULL OR :search = '' OR c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')) " +
           "ORDER BY c.name ASC")
    Page<Course> searchActiveCourses(
            @Param("search") String search,
            Pageable pageable);

    /**
     * Admin course table: filters in the database; per-course access is checked by the caller.
     * Program and domain are fetched with the course so neither the access check nor the
     * mapping issues one query per course. Empty strings mean "no filter".
     */
    @Query("SELECT c FROM Course c JOIN FETCH c.program p JOIN FETCH p.domain " +
           "WHERE (:programId = '' OR p.id = :programId) " +
           "AND (:status = '' OR c.status = :status) " +
           "AND (:search = '' OR c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')) " +
           "ORDER BY c.code ASC, c.name ASC, c.id ASC")
    List<Course> findAdminList(
            @Param("search") String search,
            @Param("status") String status,
            @Param("programId") String programId);

    @Query("select distinct c from Course c join c.unitGrants ug where key(ug) = :unitId and c.status <> 'DELETED'")
    List<Course> findByUnitGrant(@Param("unitId") String unitId);

    /**
     * Learnable courses granted as MANDATORY to any of {@code unitIds} (the learner's unit and its ancestors).
     * Same result as filtering every course with AccessPolicy.mandatory, without loading the catalog.
     */
    @Query(value = """
        select distinct c.id from courses c join course_unit_grants g on g.course_id = c.id
        where g.unit_id in (:unitIds) and upper(g.grant_type) = 'MANDATORY'
          and (c.status is null or upper(c.status) in ('PUBLISHED', 'ACTIVE'))
        """, nativeQuery = true)
    List<String> findMandatoryCourseIds(@Param("unitIds") Collection<String> unitIds);

    /**
     * Admin "deadlines per learner" list, paged by the database. Same rule as
     * LearnerDeadlineServiceImpl.enrolled: a published course the learner registered for
     * (when {@code allowRegistered}), or one granted MANDATORY to any of {@code mandatoryUnitIds}.
     * Pass a non-matching sentinel such as "" when there are no units; empty search means no filter.
     */
    @Query(value = "SELECT c FROM Course c WHERE c.status = 'PUBLISHED' " +
           "AND ((:allowRegistered = true AND EXISTS (SELECT 1 FROM User u JOIN u.registeredCourses rc " +
           "WHERE u.id = :userId AND rc.id = c.id)) " +
           "OR EXISTS (SELECT 1 FROM Course c2 JOIN c2.unitGrants g WHERE c2.id = c.id " +
           "AND KEY(g) IN :mandatoryUnitIds AND UPPER(VALUE(g)) = 'MANDATORY')) " +
           "AND (:search = '' OR c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%') " +
           "OR c.id ILIKE CONCAT('%', :search, '%')) " +
           "ORDER BY c.name ASC, c.id ASC",
           countQuery = "SELECT count(c) FROM Course c WHERE c.status = 'PUBLISHED' " +
           "AND ((:allowRegistered = true AND EXISTS (SELECT 1 FROM User u JOIN u.registeredCourses rc " +
           "WHERE u.id = :userId AND rc.id = c.id)) " +
           "OR EXISTS (SELECT 1 FROM Course c2 JOIN c2.unitGrants g WHERE c2.id = c.id " +
           "AND KEY(g) IN :mandatoryUnitIds AND UPPER(VALUE(g)) = 'MANDATORY')) " +
           "AND (:search = '' OR c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%') " +
           "OR c.id ILIKE CONCAT('%', :search, '%'))")
    Page<Course> findLearnerDeadlineCourses(
            @Param("userId") Long userId,
            @Param("allowRegistered") boolean allowRegistered,
            @Param("mandatoryUnitIds") Collection<String> mandatoryUnitIds,
            @Param("search") String search,
            Pageable pageable);
}
