package com.bidv.coretraining.dto;

import lombok.Data;

@Data
public class QuizGenerationSourceDto {
    private String id;
    private String documentId;
    private String documentName;
    private Integer page;
    private String section;
}
