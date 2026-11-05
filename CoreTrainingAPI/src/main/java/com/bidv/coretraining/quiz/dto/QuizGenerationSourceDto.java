package com.bidv.coretraining.quiz.dto;

import lombok.Data;

@Data
public class QuizGenerationSourceDto {
    private String id;
    private String documentId;
    private String documentName;
    private Integer page;
    private String section;
}
