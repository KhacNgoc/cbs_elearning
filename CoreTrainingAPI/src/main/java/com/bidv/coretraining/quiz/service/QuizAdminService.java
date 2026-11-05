package com.bidv.coretraining.quiz.service;


import com.bidv.coretraining.quiz.dto.QuestionDto;
import com.bidv.coretraining.quiz.dto.QuizGenerationJobDto;
import com.bidv.coretraining.quiz.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.quiz.dto.QuizGenerationResponseDto;
import com.bidv.coretraining.quiz.dto.QuizSettingsDto;
import java.util.List;

public interface QuizAdminService {
    List<QuestionDto> getQuizQuestions(String courseId, String lessonId);

    QuestionDto createQuestion(QuestionDto questionDto);

    QuestionDto updateQuestion(Long id, QuestionDto questionDto);

    void deleteQuestion(Long id);

    QuizSettingsDto updateQuizSettings(QuizSettingsDto settings);

    void deleteQuiz(String courseId, String lessonId);

    QuizGenerationResponseDto generateQuiz(QuizGenerationRequestDto request);

    QuizGenerationJobDto startQuizGenerationJob(QuizGenerationRequestDto request);

    QuizGenerationJobDto getQuizGenerationJob(String jobId);
}
