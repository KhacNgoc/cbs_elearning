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
public class QuizReviewDto {
    private Integer score;
    private Integer total;
    private Integer percent;
    private Integer passingScore;
    private Boolean passed;
    private List<QuizReviewQuestionDto> questions;
}
