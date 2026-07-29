package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.QuizService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;

@Service
public class QuizServiceImpl implements QuizService {

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final UserQuizScoreRepository userQuizScoreRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final QuizMapper quizMapper;

    public QuizServiceImpl(QuestionRepository questionRepository, TopicRepository topicRepository,
                           LessonRepository lessonRepository,
                           UserRepository userRepository, UserQuizScoreRepository userQuizScoreRepository,
                           BadgeRepository badgeRepository, UserBadgeRepository userBadgeRepository,
                           QuizMapper quizMapper) {
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.userQuizScoreRepository = userQuizScoreRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.quizMapper = quizMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestionsForTopic(String topicId) {
        if (!topicRepository.existsById(topicId)) {
            throw new ResourceNotFoundException("Topic", "id", topicId);
        }
        List<Question> questions = questionRepository.findByTopicIdAndLessonIsNull(topicId);
        return quizMapper.toQuestionDtoList(questions);
    }

    @Override
    @Transactional
    public QuizResultDto submitQuiz(String topicId, QuizSubmitRequest request, String username) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        List<Question> questions = questionRepository.findByTopicIdAndLessonIsNull(topicId);
        int totalQuestions = questions.size();
        if (totalQuestions == 0) {
            return QuizResultDto.builder().score(0).total(0).percent(0)
                    .passingScore(topic.getQuizPassingScore()).passed(false).build();
        }

        int correctCount = 0;
        for (Question question : questions) {
            Integer selectedAnswer = request.getSelectedAnswers().get(question.getId());
            if (selectedAnswer != null && selectedAnswer.equals(question.getCorrectAnswer())) {
                correctCount++;
            }
        }

        int percent = Math.round(((float) correctCount / totalQuestions) * 100);
        int passingScore = topic.getQuizPassingScore();

        // Update high score
        Optional<UserQuizScore> existingScoreOpt =
                userQuizScoreRepository.findByUserIdAndTopicIdAndLessonIsNull(user.getId(), topicId);
        int previousHighScore = 0;

        if (existingScoreOpt.isPresent()) {
            UserQuizScore existingScore = existingScoreOpt.get();
            previousHighScore = existingScore.getScore();
            if (correctCount >= previousHighScore) {
                existingScore.setScore(correctCount);
                existingScore.setTotal(totalQuestions);
                existingScore.setTakenAt(LocalDateTime.now());
                existingScore.setSelectedAnswers(new HashMap<>(request.getSelectedAnswers()));
                userQuizScoreRepository.save(existingScore);

                // Add points for score improvement
                if (correctCount > previousHighScore) {
                    int improvedQuestions = correctCount - previousHighScore;
                    user.setPoints(user.getPoints() + improvedQuestions * 50);
                }
            }
        } else {
            UserQuizScore quizScore = UserQuizScore.builder()
                    .user(user)
                    .topic(topic)
                    .score(correctCount)
                    .total(totalQuestions)
                    .takenAt(LocalDateTime.now())
                    .selectedAnswers(new HashMap<>(request.getSelectedAnswers()))
                    .build();
            userQuizScoreRepository.save(quizScore);

            // Add points for initial attempt
            user.setPoints(user.getPoints() + correctCount * 50);
        }

        // Check and unlock badges
        checkAndUnlockBadges(user, topic, correctCount, totalQuestions);

        // Passing the topic-level assessment completes the whole course/topic.
        // Only newly completed lessons receive the normal completion points.
        if (percent >= passingScore) {
            int newlyCompletedLessons = 0;
            for (Lesson lesson : topic.getLessons()) {
                if (user.getCompletedLessons().add(lesson)) {
                    newlyCompletedLessons++;
                }
            }
            if (newlyCompletedLessons > 0) {
                user.setPoints(user.getPoints() + newlyCompletedLessons * 100);
            }
        }

        userRepository.save(user);

        return QuizResultDto.builder()
                .score(correctCount)
                .total(totalQuestions)
                .percent(percent)
                .passingScore(passingScore)
                .passed(percent >= passingScore)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestionsForLesson(String lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }
        return quizMapper.toQuestionDtoList(questionRepository.findByLessonId(lessonId));
    }

    @Override
    @Transactional
    public QuizResultDto submitLessonQuiz(String lessonId, QuizSubmitRequest request, String username) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        List<Question> questions = questionRepository.findByLessonId(lessonId);
        int total = questions.size();
        int score = 0;
        for (Question question : questions) {
            Integer selected = request.getSelectedAnswers().get(question.getId());
            if (selected != null && selected.equals(question.getCorrectAnswer())) score++;
        }
        int percent = total == 0 ? 0 : Math.round((float) score / total * 100);
        int passingScore = lesson.getQuizPassingScore();
        Optional<UserQuizScore> existingScore =
                userQuizScoreRepository.findByUserIdAndLessonId(user.getId(), lessonId);
        if (existingScore.isPresent()) {
            UserQuizScore quizScore = existingScore.get();
            if (score >= quizScore.getScore()) {
                quizScore.setScore(score);
                quizScore.setTotal(total);
                quizScore.setTakenAt(LocalDateTime.now());
                quizScore.setSelectedAnswers(new HashMap<>(request.getSelectedAnswers()));
                userQuizScoreRepository.save(quizScore);
            }
        } else if (total > 0) {
            userQuizScoreRepository.save(UserQuizScore.builder()
                    .user(user)
                    .topic(lesson.getTopic())
                    .lesson(lesson)
                    .score(score)
                    .total(total)
                    .takenAt(LocalDateTime.now())
                    .selectedAnswers(new HashMap<>(request.getSelectedAnswers()))
                    .build());
        }
        if (total > 0 && percent >= passingScore && !user.getCompletedLessons().contains(lesson)) {
            user.getCompletedLessons().add(lesson);
            user.setPoints(user.getPoints() + 100);
            userRepository.save(user);
        }
        return QuizResultDto.builder().score(score).total(total).percent(percent)
                .passingScore(passingScore).passed(total > 0 && percent >= passingScore).build();
    }

    @Override
    @Transactional(readOnly = true)
    public QuizReviewDto getTopicQuizReview(String topicId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));
        UserQuizScore score = userQuizScoreRepository
                .findByUserIdAndTopicIdAndLessonIsNull(user.getId(), topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt", "topicId", topicId));
        return buildReview(score, questionRepository.findByTopicIdAndLessonIsNull(topicId),
                topic.getQuizPassingScore());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizReviewDto getLessonQuizReview(String lessonId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        UserQuizScore score = userQuizScoreRepository.findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt", "lessonId", lessonId));
        return buildReview(score, questionRepository.findByLessonId(lessonId), lesson.getQuizPassingScore());
    }

    private QuizReviewDto buildReview(UserQuizScore score, List<Question> questions, int passingScore) {
        int percent = score.getTotal() == 0 ? 0
                : Math.round((float) score.getScore() / score.getTotal() * 100);
        if (percent < passingScore) {
            throw new BadRequestException("Chỉ có thể xem lại bài kiểm tra đã đạt");
        }
        if (score.getSelectedAnswers() == null || score.getSelectedAnswers().isEmpty()) {
            throw new BadRequestException("Lần làm bài cũ chưa có dữ liệu đáp án để xem lại");
        }
        List<QuizReviewQuestionDto> reviewQuestions = questions.stream()
                .map(question -> {
                    Integer selected = score.getSelectedAnswers().get(question.getId());
                    return QuizReviewQuestionDto.builder()
                            .id(question.getId())
                            .text(question.getText())
                            .options(question.getOptions())
                            .selectedAnswer(selected)
                            .correctAnswer(question.getCorrectAnswer())
                            .correct(selected != null && selected.equals(question.getCorrectAnswer()))
                            .build();
                })
                .toList();
        return QuizReviewDto.builder()
                .score(score.getScore())
                .total(score.getTotal())
                .percent(percent)
                .passingScore(passingScore)
                .passed(true)
                .questions(reviewQuestions)
                .build();
    }

    private void checkAndUnlockBadges(User user, Topic topic, int score, int total) {
        float ratio = (float) score / total;
        String badgeType = null;
        String badgeTitle = "";
        String badgeIcon = "";
        int badgePoints = 0;

        if (ratio >= 1.0f) {
            badgeType = "gold";
            badgeTitle = "Huy hiệu Vàng " + topic.getName();
            badgeIcon = "Award";
            badgePoints = 300;
        } else if (ratio > 0.9f) {
            badgeType = "silver";
            badgeTitle = "Huy hiệu Bạc " + topic.getName();
            badgeIcon = "Medal";
            badgePoints = 200;
        } else if (ratio >= 0.8f) {
            badgeType = "bronze";
            badgeTitle = "Huy hiệu Đồng " + topic.getName();
            badgeIcon = "Shield";
            badgePoints = 100;
        }

        if (badgeType != null) {
            String badgeId = topic.getId() + "_" + badgeType;
            final String finalBadgeTitle = badgeTitle;
            final String finalBadgeIcon = badgeIcon;
            
            // Check if Badge exists in database, if not create it
            Badge badge = badgeRepository.findById(badgeId).orElseGet(() -> {
                Badge newBadge = Badge.builder()
                        .id(badgeId)
                        .title(finalBadgeTitle)
                        .type(badgeId.contains("gold") ? "gold" : (badgeId.contains("silver") ? "silver" : "bronze"))
                        .topic(topic)
                        .icon(finalBadgeIcon)
                        .build();
                return badgeRepository.save(newBadge);
            });

            // Check if user already unlocked this badge
            Optional<UserBadge> existingUserBadge = userBadgeRepository.findByUserIdAndBadgeId(user.getId(), badgeId);
            if (existingUserBadge.isEmpty()) {
                UserBadge userBadge = UserBadge.builder()
                        .user(user)
                        .badge(badge)
                        .unlockedAt(LocalDate.now())
                        .build();
                userBadgeRepository.save(userBadge);

                // Add bonus points for unlocking badge
                user.setPoints(user.getPoints() + badgePoints);
            }
        }
    }
}
