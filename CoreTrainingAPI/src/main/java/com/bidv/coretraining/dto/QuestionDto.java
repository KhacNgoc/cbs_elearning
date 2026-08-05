package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.QuestionType;
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
    private String topicId;
    private String lessonId;
    private String text;
    private List<String> options;
    private Integer correctAnswer;
    private QuestionType type;
    private List<Integer> correctAnswers;
}
