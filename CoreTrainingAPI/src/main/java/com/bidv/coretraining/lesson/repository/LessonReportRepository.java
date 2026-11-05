package com.bidv.coretraining.lesson.repository;

import com.bidv.coretraining.lesson.entity.LessonReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonReportRepository extends JpaRepository<LessonReport, Long> {
}
