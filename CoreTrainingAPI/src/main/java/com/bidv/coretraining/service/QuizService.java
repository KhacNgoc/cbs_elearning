package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.dto.QuizResultDto;
import com.bidv.coretraining.dto.QuizSubmitRequest;

import java.util.List;

public interface QuizService {
    List<QuestionDto> getQuestionsForTopic(String topicId);
    QuizResultDto submitQuiz(String topicId, QuizSubmitRequest request, String username);
    List<QuestionDto> getQuestionsForLesson(String lessonId);
    QuizResultDto submitLessonQuiz(String lessonId, QuizSubmitRequest request, String username);
}
