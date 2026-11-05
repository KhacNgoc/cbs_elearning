package com.bidv.coretraining.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResultDto {
    private Integer score;
    private Integer total;
    private Integer percent;
    private Integer passingScore;
    private Boolean passed;
}
