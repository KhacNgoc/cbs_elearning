package com.bidv.coretraining.course.repository;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.entity.CourseRating;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface CourseRatingRepository extends JpaRepository<CourseRating,Long> {
    Optional<CourseRating> findByUserIdAndCourseId(Long userId,String courseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id=:id")
    Optional<Course> lockCourse(@Param("id") String id);
    interface RatingAggregate { String getCourseId(); Long getCount(); Double getAverage(); Long getLow(); }
    @Query("select r.course.id as courseId, count(r) as count, avg(r.stars) as average, sum(case when r.stars<=2 then 1 else 0 end) as low from CourseRating r where r.course.id in :ids group by r.course.id")
    List<RatingAggregate> aggregate(@Param("ids") Collection<String> ids);
    interface StarCount { Integer getStars(); Long getCount(); }
    @Query("select r.stars as stars,count(r) as count from CourseRating r where r.course.id in :ids group by r.stars")
    List<StarCount> distribution(@Param("ids") Collection<String> ids);
    @Query("select count(u) from User u join u.completedCourses completed where key(completed)=:courseId")
    long completedCount(@Param("courseId") String courseId);
    @Query(value="select r from CourseRating r join fetch r.user join fetch r.course where r.course.id in :ids and (:low=false or r.stars<=2)",countQuery="select count(r) from CourseRating r where r.course.id in :ids and (:low=false or r.stars<=2)")
    org.springframework.data.domain.Page<CourseRating> feedback(@Param("ids") Collection<String> ids,@Param("low") boolean low,org.springframework.data.domain.Pageable page);
}
