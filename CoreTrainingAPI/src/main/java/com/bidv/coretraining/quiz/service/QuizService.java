package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.quiz.dto.QuizQuestionDto;
import com.bidv.coretraining.quiz.dto.QuizResultDto;
import com.bidv.coretraining.quiz.dto.QuizReviewDto;
import com.bidv.coretraining.quiz.dto.QuizSubmitRequest;

import java.util.List;

public interface QuizService {
    List<QuizQuestionDto> getQuestionsForCourse(String courseId);

    QuizResultDto submitQuiz(String courseId, QuizSubmitRequest request, String username);

    List<QuizQuestionDto> getQuestionsForLesson(String lessonId);

    QuizResultDto submitLessonQuiz(String lessonId, QuizSubmitRequest request, String username);

    QuizReviewDto getCourseQuizReview(String courseId, String username);

    QuizReviewDto getLessonQuizReview(String lessonId, String username);
}
