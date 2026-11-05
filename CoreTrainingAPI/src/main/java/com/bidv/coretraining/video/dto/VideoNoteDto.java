package com.bidv.coretraining.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoNoteDto {
    private Long id;
    private String lessonId;
    private Integer timestampSeconds;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
