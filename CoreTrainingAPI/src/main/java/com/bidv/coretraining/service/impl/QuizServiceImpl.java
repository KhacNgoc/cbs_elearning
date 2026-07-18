package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.QuestionDto;
import com.bidv.coretraining.dto.QuizMapper;
import com.bidv.coretraining.dto.QuizResultDto;
import com.bidv.coretraining.dto.QuizSubmitRequest;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.QuizService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class QuizServiceImpl implements QuizService {

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final UserQuizScoreRepository userQuizScoreRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final QuizMapper quizMapper;

    public QuizServiceImpl(QuestionRepository questionRepository, TopicRepository topicRepository,
                           UserRepository userRepository, UserQuizScoreRepository userQuizScoreRepository,
                           BadgeRepository badgeRepository, UserBadgeRepository userBadgeRepository,
                           QuizMapper quizMapper) {
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
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
        List<Question> questions = questionRepository.findByTopicId(topicId);
        return quizMapper.toQuestionDtoList(questions);
    }

    @Override
    @Transactional
    public QuizResultDto submitQuiz(String topicId, QuizSubmitRequest request, String username) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        List<Question> questions = questionRepository.findByTopicId(topicId);
        int totalQuestions = questions.size();
        if (totalQuestions == 0) {
            return QuizResultDto.builder().score(0).total(0).percent(0).build();
        }

        int correctCount = 0;
        for (Question question : questions) {
            Integer selectedAnswer = request.getSelectedAnswers().get(question.getId());
            if (selectedAnswer != null && selectedAnswer.equals(question.getCorrectAnswer())) {
                correctCount++;
            }
        }

        int percent = Math.round(((float) correctCount / totalQuestions) * 100);

        // Update high score
        Optional<UserQuizScore> existingScoreOpt = userQuizScoreRepository.findByUserIdAndTopicId(user.getId(), topicId);
        int previousHighScore = 0;

        if (existingScoreOpt.isPresent()) {
            UserQuizScore existingScore = existingScoreOpt.get();
            previousHighScore = existingScore.getScore();
            if (correctCount > previousHighScore) {
                existingScore.setScore(correctCount);
                existingScore.setTotal(totalQuestions);
                existingScore.setTakenAt(LocalDateTime.now());
                userQuizScoreRepository.save(existingScore);

                // Add points for score improvement
                int improvedQuestions = correctCount - previousHighScore;
                user.setPoints(user.getPoints() + improvedQuestions * 50);
            }
        } else {
            UserQuizScore quizScore = UserQuizScore.builder()
                    .user(user)
                    .topic(topic)
                    .score(correctCount)
                    .total(totalQuestions)
                    .takenAt(LocalDateTime.now())
                    .build();
            userQuizScoreRepository.save(quizScore);

            // Add points for initial attempt
            user.setPoints(user.getPoints() + correctCount * 50);
        }

        // Check and unlock badges
        checkAndUnlockBadges(user, topic, correctCount, totalQuestions);

        userRepository.save(user);

        return QuizResultDto.builder()
                .score(correctCount)
                .total(totalQuestions)
                .percent(percent)
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
