package com.bidv.coretraining.lesson.dto;

import java.time.LocalDateTime;

public record LessonChangeLogDto(
    Long id,
    String lessonId,
    Long version,
    String changeSummary,
    String updatedBy,
    LocalDateTime createdAt
) {}
