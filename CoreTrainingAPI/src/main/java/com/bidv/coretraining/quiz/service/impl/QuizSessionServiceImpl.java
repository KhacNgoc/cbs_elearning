package com.bidv.coretraining.quiz.service.impl;

import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.quiz.dto.QuizSessionDto;
import com.bidv.coretraining.quiz.entity.QuizSession;
import com.bidv.coretraining.quiz.repository.QuizSessionRepository;
import com.bidv.coretraining.quiz.service.QuizSessionService;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class QuizSessionServiceImpl implements QuizSessionService {
    /** Covers network latency of the client's auto-submit fired exactly at the deadline. */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    private final QuizSessionRepository sessions;
    private final LessonRepository lessons;
    private final UserRepository users;

    public QuizSessionServiceImpl(QuizSessionRepository sessions, LessonRepository lessons, UserRepository users) {
        this.sessions = sessions;
        this.lessons = lessons;
        this.users = users;
    }

    @Override
    @Transactional
    public QuizSessionDto start(String lessonId, String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        // Serialises concurrent starts so one learner cannot hold two live sessions.
        users.lockForLearning(user.getId()).orElseThrow();
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        LearningPolicy.assertLessonAccess(user, lesson);
        if (lesson.getQuiz() == null)
            throw new BadRequestException("Bài học chưa có kiểm tra");
        Integer limit = lesson.getQuiz().getTimeLimitMinutes();
        if (limit == null)
            return new QuizSessionDto(null, null, null);
        LocalDateTime now = LocalDateTime.now();
        QuizSession session = sessions
                .findFirstByUserIdAndLessonIdAndSubmittedAtIsNullAndDeadlineAtAfterOrderByStartedAtDesc(
                        user.getId(), lessonId, now)
                .orElseGet(() -> sessions.save(QuizSession.builder().user(user).lesson(lesson)
                        .startedAt(now).deadlineAt(now.plusMinutes(limit)).build()));
        return new QuizSessionDto(session.getId(), limit,
                Math.max(0, Duration.between(now, session.getDeadlineAt()).toSeconds()));
    }

    @Override
    @Transactional
    public void consume(User user, Lesson lesson, String sessionId) {
        if (lesson.getQuiz() == null || lesson.getQuiz().getTimeLimitMinutes() == null)
            return;
        QuizSession session = sessionId == null ? null : sessions.findById(sessionId).orElse(null);
        if (session == null || session.getSubmittedAt() != null
                || !session.getUser().getId().equals(user.getId())
                || !session.getLesson().getId().equals(lesson.getId()))
            throw new BadRequestException("Phiên làm bài không hợp lệ hoặc đã được nộp. Vui lòng làm lại bài kiểm tra.");
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(session.getDeadlineAt().plus(SUBMIT_GRACE)))
            throw new BadRequestException("Đã hết thời gian làm bài nên bài nộp không được ghi nhận. Vui lòng làm lại bài kiểm tra.");
        session.setSubmittedAt(now);
    }
}
