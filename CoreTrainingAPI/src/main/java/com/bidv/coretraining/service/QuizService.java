package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizQuestionDto;
import com.bidv.coretraining.dto.QuizResultDto;
import com.bidv.coretraining.dto.QuizSubmitRequest;
import com.bidv.coretraining.dto.QuizReviewDto;

import java.util.List;

public interface QuizService {
    List<QuizQuestionDto> getQuestionsForCourse(String courseId);
    QuizResultDto submitQuiz(String courseId, QuizSubmitRequest request, String username);
    List<QuizQuestionDto> getQuestionsForLesson(String lessonId);
    QuizResultDto submitLessonQuiz(String lessonId, QuizSubmitRequest request, String username);
    QuizReviewDto getCourseQuizReview(String courseId, String username);
    QuizReviewDto getLessonQuizReview(String lessonId, String username);
}
