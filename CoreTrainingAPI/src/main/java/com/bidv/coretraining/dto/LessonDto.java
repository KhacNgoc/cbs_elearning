package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.VideoSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDto {
    private String id;
    private String title;
    private String topicId;
    private String hint;
    private String videoUrl;
    private VideoSource videoSource;
    private List<LessonDocumentDto> documents;
    private List<ProcedureSectionDto> procedureSteps;
    private List<CommentDto> comments;
}
