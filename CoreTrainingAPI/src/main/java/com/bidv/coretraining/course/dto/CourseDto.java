package com.bidv.coretraining.course.dto;

import com.bidv.coretraining.lesson.dto.LessonDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDto {
    private java.util.Set<String> allowedActions;
    private java.util.Map<String, java.util.Set<String>> adminActions;
    private String id;
    private Boolean canEdit;
    private String code;
    private String programId;
    private String programName;
    private String domainName;
    private String domainId;
    private String thumbnail;
    private String businessModule;
    private Integer seqNumber;
    private String level;
    private String keywords;
    private java.time.LocalDate completionDeadline;
    private String status;
    private String description;
    private Long version;
    private String name;
    private String iconName;
    private Boolean hasQuiz;
    private Integer quizPassingScore;
    private List<LessonDto> lessons;
    private java.util.Set<String> mandatoryUnitIds;
    private java.util.Set<String> optionalUnitIds;
    private java.util.Set<String> unitIds;
}
