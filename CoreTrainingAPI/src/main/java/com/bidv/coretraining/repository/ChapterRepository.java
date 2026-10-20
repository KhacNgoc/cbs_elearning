package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, String> {
    java.util.List<Chapter> findByCourseIdOrderByChapterOrderAscIdAsc(String courseId);
}
