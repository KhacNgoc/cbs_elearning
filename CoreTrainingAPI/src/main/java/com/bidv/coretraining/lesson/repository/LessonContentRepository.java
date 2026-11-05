package com.bidv.coretraining.lesson.repository;

import com.bidv.coretraining.lesson.entity.LessonContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonContentRepository extends JpaRepository<LessonContent, String> {
}
