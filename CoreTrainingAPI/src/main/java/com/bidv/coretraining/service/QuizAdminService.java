package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;

import java.util.List;

public interface QuizAdminService {
    List<QuestionDto> getQuizQuestions(String courseId, String lessonId);

    QuestionDto createQuestion(QuestionDto questionDto);

    QuestionDto updateQuestion(Long id, QuestionDto questionDto);

    void deleteQuestion(Long id);

    QuizSettingsDto updateQuizSettings(QuizSettingsDto settings);

    void deleteQuiz(String courseId, String lessonId);

    QuizGenerationResponseDto generateQuiz(QuizGenerationRequestDto request);
}
