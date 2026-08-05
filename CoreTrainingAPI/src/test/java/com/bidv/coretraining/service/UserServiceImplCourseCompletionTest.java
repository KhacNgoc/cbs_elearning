package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.UserMapper;
import com.bidv.coretraining.entity.EnrollmentStatus;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.UserQuizScore;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.QuestionRepository;
import com.bidv.coretraining.repository.TopicEnrollmentRequestRepository;
import com.bidv.coretraining.repository.TopicRepository;
import com.bidv.coretraining.repository.UserQuizScoreRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplCourseCompletionTest {
    private UserRepository userRepository;
    private LessonRepository lessonRepository;
    private QuestionRepository questionRepository;
    private UserQuizScoreRepository userQuizScoreRepository;
    private UserServiceImpl service;
    private User user;
    private Topic topic;
    private Lesson firstLesson;
    private Lesson finalLesson;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        lessonRepository = mock(LessonRepository.class);
        questionRepository = mock(QuestionRepository.class);
        userQuizScoreRepository = mock(UserQuizScoreRepository.class);
        UserMapper userMapper = mock(UserMapper.class);
        TopicEnrollmentRequestRepository enrollmentRepository = mock(TopicEnrollmentRequestRepository.class);
        LessonProgressService lessonProgressService = mock(LessonProgressService.class);

        service = new UserServiceImpl(
                userRepository,
                lessonRepository,
                questionRepository,
                mock(TopicRepository.class),
                userMapper,
                enrollmentRepository,
                mock(NotificationService.class),
                mock(PasswordEncoder.class),
                lessonProgressService,
                userQuizScoreRepository);

        topic = Topic.builder().id("COURSE").name("Khóa học").quizPassingScore(80).build();
        firstLesson = Lesson.builder().id("LESSON-1").title("Bài 1").topic(topic).build();
        finalLesson = Lesson.builder().id("LESSON-2").title("Bài 2").topic(topic).build();
        topic.setLessons(List.of(firstLesson, finalLesson));
        user = User.builder()
                .id(1L)
                .username("learner")
                .password("encoded")
                .fullName("Học viên")
                .role("ROLE_USER")
                .points(0)
                .build();

        when(userRepository.findByUsername("learner")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionRepository.existsByLessonId(any())).thenReturn(false);
        when(userMapper.toUserBadgeDtoList(anyList())).thenReturn(List.of());
        when(enrollmentRepository.findByUserIdAndStatus(1L, EnrollmentStatus.PENDING)).thenReturn(List.of());
        when(lessonProgressService.getAllProgress(user)).thenReturn(Map.of());
    }

    @Test
    void blocksOnlyTheCompletionThatWouldFinishAllLessonsBeforeRequiredTopicQuiz() {
        user.getCompletedLessons().add(firstLesson);
        when(lessonRepository.findById(finalLesson.getId())).thenReturn(Optional.of(finalLesson));
        when(questionRepository.existsByTopicIdAndLessonIsNull(topic.getId())).thenReturn(true);
        when(userQuizScoreRepository.findByUserIdAndTopicIdAndLessonIsNull(1L, topic.getId()))
                .thenReturn(Optional.empty());

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.completeLesson("learner", finalLesson.getId()));

        assertEquals("Bạn cần hoàn thành bài kiểm tra tổng kết để hoàn thành khóa học", error.getMessage());
        verify(userRepository, never()).save(any(User.class));
        assertTrue(!user.getCompletedLessons().contains(finalLesson));
    }

    @Test
    void allowsCompletingAnEarlierLessonWhileOtherLessonsRemain() {
        when(lessonRepository.findById(firstLesson.getId())).thenReturn(Optional.of(firstLesson));
        when(questionRepository.existsByTopicIdAndLessonIsNull(topic.getId())).thenReturn(true);
        when(userQuizScoreRepository.findByUserIdAndTopicIdAndLessonIsNull(1L, topic.getId()))
                .thenReturn(Optional.empty());

        service.completeLesson("learner", firstLesson.getId());

        assertTrue(user.getCompletedLessons().contains(firstLesson));
        verify(userRepository).save(user);
    }

    @Test
    void allowsFinalLessonAfterPassingTheTopicQuiz() {
        user.getCompletedLessons().add(firstLesson);
        UserQuizScore passingScore = UserQuizScore.builder()
                .user(user)
                .topic(topic)
                .score(8)
                .total(10)
                .build();
        when(lessonRepository.findById(finalLesson.getId())).thenReturn(Optional.of(finalLesson));
        when(questionRepository.existsByTopicIdAndLessonIsNull(topic.getId())).thenReturn(true);
        when(userQuizScoreRepository.findByUserIdAndTopicIdAndLessonIsNull(1L, topic.getId()))
                .thenReturn(Optional.of(passingScore));

        service.completeLesson("learner", finalLesson.getId());

        assertTrue(user.getCompletedLessons().contains(finalLesson));
    }
}
