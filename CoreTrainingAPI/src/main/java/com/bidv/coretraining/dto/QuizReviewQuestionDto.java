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
public class QuizReviewQuestionDto {
    private Long id;
    private String text;
    private List<String> options;
    private Integer selectedAnswer;
    private Integer correctAnswer;
    private Boolean correct;
}
