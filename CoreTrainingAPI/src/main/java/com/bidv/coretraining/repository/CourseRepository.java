package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Course;
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
}
