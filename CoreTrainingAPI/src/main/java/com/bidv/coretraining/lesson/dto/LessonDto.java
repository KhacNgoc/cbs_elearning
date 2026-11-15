package com.bidv.coretraining.lesson.dto;

import com.bidv.coretraining.document.dto.LessonDocumentDto;
import com.bidv.coretraining.enums.VideoSource;
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
    private String courseId;
    private String chapterId;
    private String status;
    private Long version;
    private String textBody;
    private Boolean quizRequired;
    private Integer lessonOrder;
    private String hint;
    private String videoUrl;
    private VideoSource videoSource;
    private Boolean hasQuiz;
    private Boolean hasVideo;
    private Boolean hasText;
    /** Outline flag for editors: the lesson has procedure steps. Ignored on update. */
    private Boolean hasProcedure;
    /** On update, null keeps the stored value. */
    private Boolean textRequired;
    private Boolean videoRequired;
    private Boolean slideRequired;
    private Integer quizPassingScore;
    private List<LessonDocumentDto> documents;
    private List<ProcedureSectionDto> procedureSteps;
    private List<CommentDto> comments;
    private Long commentCount;
    /** Outline-only counts so lists can show badges without loading documents or procedure steps. */
    private Integer documentCount;
    private Integer procedureStepCount;
}
