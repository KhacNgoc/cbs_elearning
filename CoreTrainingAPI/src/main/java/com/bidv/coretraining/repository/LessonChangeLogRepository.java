package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.LessonChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LessonChangeLogRepository extends JpaRepository<LessonChangeLog, Long> {
    Optional<LessonChangeLog> findTopByLessonIdOrderByCreatedAtDesc(String lessonId);
    List<LessonChangeLog> findByLessonIdOrderByCreatedAtDesc(String lessonId);
}
