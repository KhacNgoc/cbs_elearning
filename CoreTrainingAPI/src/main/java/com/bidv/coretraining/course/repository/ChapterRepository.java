package com.bidv.coretraining.course.repository;

import com.bidv.coretraining.course.entity.Chapter;
import com.bidv.coretraining.course.repository.projection.PublishedChapterView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChapterRepository extends JpaRepository<Chapter, String> {
    java.util.List<Chapter> findByCourseIdOrderByChapterOrderAscIdAsc(String courseId);

    @Query("select ch.id as id, ch.course.id as courseId from Chapter ch where ch.course.id in :courseIds "
            + "and ch.status = 'PUBLISHED' order by ch.chapterOrder, ch.id")
    java.util.List<PublishedChapterView> findPublishedOrder(@Param("courseIds") java.util.Collection<String> courseIds);
}
