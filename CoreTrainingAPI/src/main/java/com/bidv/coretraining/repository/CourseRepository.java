package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, String> {
    @org.springframework.data.jpa.repository.Query("select distinct c from Course c left join c.instructorIds i where (:admin = true and c.ownerUnit.id in :units) or (:instructor = true and i = :userId)")
    java.util.List<Course> findEditable(
            @org.springframework.data.repository.query.Param("units") java.util.Collection<String> units,
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("admin") boolean admin,
            @org.springframework.data.repository.query.Param("instructor") boolean instructor);

}
