package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, String> {
    @org.springframework.data.jpa.repository.Query("select distinct l from Lesson l left join l.documents d where d.fileUrl=:path or l.content.videoUrl=:path")
    java.util.List<Lesson> findByAssetPath(@org.springframework.data.repository.query.Param("path") String path);

    List<Lesson> findByChapterIdOrderByLessonOrderAscIdAsc(String chapterId);

    List<Lesson> findByCourseIdOrderByLessonOrderAscIdAsc(String courseId);
}
