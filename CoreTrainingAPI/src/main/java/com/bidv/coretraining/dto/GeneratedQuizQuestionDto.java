package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.QuestionType;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class GeneratedQuizQuestionDto {
    private QuestionType type;
    private String text;
    private List<String> options;
    @JsonAlias("correct_answers")
    private List<Integer> correctAnswers;
    private String explanation;
    @JsonAlias("source_ids")
    private List<String> sourceIds;
}
