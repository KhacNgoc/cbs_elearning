package com.bidv.coretraining.dto;

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
    private String id;
    private String code;
    private String programId;
    private String programName;
    private String domainName;
    private String status;
    private String description;
    private Long version;
    private String name;
    private String iconName;
    private Boolean hasQuiz;
    private Integer quizPassingScore;
    private List<LessonDto> lessons;
}
