package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizMapper;
import com.bidv.coretraining.dto.QuizResultDto;
import com.bidv.coretraining.dto.QuizSubmitRequest;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Question;
import com.bidv.coretraining.entity.QuestionType;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.repository.BadgeRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.QuestionRepository;
import com.bidv.coretraining.repository.TopicRepository;
import com.bidv.coretraining.repository.UserBadgeRepository;
import com.bidv.coretraining.repository.UserQuizScoreRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.impl.QuizServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizServiceImplCourseCompletionTest {

    @Test
    void multipleChoiceRequiresTheExactAnswerSet() {
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        LessonRepository lessonRepository = mock(LessonRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserQuizScoreRepository scoreRepository = mock(UserQuizScoreRepository.class);
        QuizServiceImpl service = new QuizServiceImpl(
                questionRepository, mock(TopicRepository.class), lessonRepository,
                userRepository, scoreRepository, mock(BadgeRepository.class),
                mock(UserBadgeRepository.class), mock(QuizMapper.class));

        Topic topic = Topic.builder().id("COURSE").name("Khóa học").build();
        Lesson lesson = Lesson.builder().id("LESSON").title("Bài học").topic(topic).quizPassingScore(80).build();
        User user = User.builder().id(1L).username("learner").password("encoded")
                .fullName("Học viên").role("ROLE_USER").build();
        Question question = Question.builder()
                .id(20L).topic(topic).lesson(lesson).text("Chọn hai đáp án")
                .options(List.of("A", "B", "C", "D"))
                .type(QuestionType.MULTIPLE_CHOICE)
                .correctAnswer(0)
                .correctAnswers(List.of(0, 2))
                .build();
        QuizSubmitRequest request = new QuizSubmitRequest();
        request.setSelectedAnswers(Map.of(20L, List.of(2, 0)));

        when(lessonRepository.findById("LESSON")).thenReturn(Optional.of(lesson));
        when(userRepository.findByUsername("learner")).thenReturn(Optional.of(user));
        when(questionRepository.findByLessonId("LESSON")).thenReturn(List.of(question));
        when(scoreRepository.findByUserIdAndLessonId(1L, "LESSON")).thenReturn(Optional.empty());
        when(questionRepository.existsByTopicIdAndLessonIsNull("COURSE")).thenReturn(false);

        QuizResultDto result = service.submitLessonQuiz("LESSON", request, "learner");

        assertEquals(1, result.getScore());
        assertTrue(result.getPassed());
    }

    @Test
    void passingFinalLessonQuizDoesNotBypassRequiredTopicQuiz() {
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        LessonRepository lessonRepository = mock(LessonRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserQuizScoreRepository scoreRepository = mock(UserQuizScoreRepository.class);
        QuizServiceImpl service = new QuizServiceImpl(
                questionRepository,
                mock(TopicRepository.class),
                lessonRepository,
                userRepository,
                scoreRepository,
                mock(BadgeRepository.class),
                mock(UserBadgeRepository.class),
                mock(QuizMapper.class));

        Topic topic = Topic.builder().id("COURSE").name("Khóa học").quizPassingScore(80).build();
        Lesson firstLesson = Lesson.builder().id("LESSON-1").title("Bài 1").topic(topic).build();
        Lesson finalLesson = Lesson.builder()
                .id("LESSON-2")
                .title("Bài 2")
                .topic(topic)
                .quizPassingScore(80)
                .build();
        topic.setLessons(List.of(firstLesson, finalLesson));
        User user = User.builder()
                .id(1L)
                .username("learner")
                .password("encoded")
                .fullName("Học viên")
                .role("ROLE_USER")
                .build();
        user.getCompletedLessons().add(firstLesson);
        Question lessonQuestion = Question.builder()
                .id(10L)
                .topic(topic)
                .lesson(finalLesson)
                .text("Câu hỏi")
                .options(List.of("Đúng", "Sai"))
                .correctAnswer(0)
                .build();
        QuizSubmitRequest request = new QuizSubmitRequest();
        request.setSelectedAnswers(Map.of(10L, List.of(0)));

        when(lessonRepository.findById(finalLesson.getId())).thenReturn(Optional.of(finalLesson));
        when(userRepository.findByUsername("learner")).thenReturn(Optional.of(user));
        when(questionRepository.findByLessonId(finalLesson.getId())).thenReturn(List.of(lessonQuestion));
        when(questionRepository.existsByTopicIdAndLessonIsNull(topic.getId())).thenReturn(true);
        when(scoreRepository.findByUserIdAndLessonId(1L, finalLesson.getId())).thenReturn(Optional.empty());
        when(scoreRepository.findByUserIdAndTopicIdAndLessonIsNull(1L, topic.getId()))
                .thenReturn(Optional.empty());

        QuizResultDto result = service.submitLessonQuiz(finalLesson.getId(), request, "learner");

        assertTrue(result.getPassed());
        assertTrue(!user.getCompletedLessons().contains(finalLesson));
        verify(userRepository, never()).save(any(User.class));
    }
}
