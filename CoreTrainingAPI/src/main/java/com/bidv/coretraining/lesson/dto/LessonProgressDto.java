package com.bidv.coretraining.lesson.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonProgressDto {
    private boolean lessonCompleted;
    private boolean textCompleted;
    private String lessonId;
    private Double lastPositionSeconds;
    private Double watchedSeconds;
    private Double durationSeconds;
    private java.time.LocalDateTime updatedAt;
    private String contentType;
    private Long documentId;
    private Integer documentPage;
    private Double scrollOffset;
    private java.util.Map<String, com.bidv.coretraining.lesson.entity.LessonProgress.DocumentPosition> documentPositions;
    private boolean contentCompleted;
}
