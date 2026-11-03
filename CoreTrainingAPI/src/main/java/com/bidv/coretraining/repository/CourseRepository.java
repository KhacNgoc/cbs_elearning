package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Course;
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

    @Query("select distinct c from Course c join c.unitGrants ug where key(ug) = :unitId and c.status <> 'DELETED'")
    List<Course> findByUnitGrant(@Param("unitId") String unitId);
}
