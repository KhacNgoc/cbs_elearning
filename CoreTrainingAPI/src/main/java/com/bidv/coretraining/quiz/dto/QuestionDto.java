package com.bidv.coretraining.quiz.dto;

import com.bidv.coretraining.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDto {
    private Long id;
    private String courseId;
    private String lessonId;
    private String text;
    private String explanation;
    private Long bankQuestionId;
    private String bankQuestionCode;
    private List<String> options;
    private Integer correctAnswer;
    private QuestionType type;
    private List<Integer> correctAnswers;
}
