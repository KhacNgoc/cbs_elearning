package com.bidv.coretraining.quiz.dto;
import com.bidv.coretraining.enums.QuestionType;
import com.bidv.coretraining.quiz.entity.BankQuestion;

import java.time.LocalDateTime;
import java.util.List;

public record BankQuestionDto(Long id, Long version, String code, String courseId, String courseName,
                String lessonId, String lessonTitle, String domainId, String domainName, String text, QuestionType type,
                List<String> options, List<Integer> correctAnswers, String explanation,
                BankQuestion.Difficulty difficulty, BankQuestion.Status status, LocalDateTime updatedAt,
                List<String> allowedActions) {
}
