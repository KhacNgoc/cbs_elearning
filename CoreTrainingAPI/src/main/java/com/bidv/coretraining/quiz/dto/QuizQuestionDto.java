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
public class QuizQuestionDto {
    private Long id;
    private String courseId;
    private String lessonId;
    private String text;
    private List<String> options;
    private QuestionType type;
    /**
     * Original index of each option in {@link #options}, in display order. Answers are submitted with
     * these original indexes, so shuffled options are graded like unshuffled ones.
     */
    private List<Integer> optionIndexes;
}
