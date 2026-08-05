package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.UserMapper;
import com.bidv.coretraining.dto.UserProfileDto;
import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.UserQuizScore;
import com.bidv.coretraining.entity.EnrollmentStatus;
import com.bidv.coretraining.entity.TopicEnrollmentRequest;
import com.bidv.coretraining.entity.NotificationType;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.QuestionRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.UserQuizScoreRepository;
import com.bidv.coretraining.repository.TopicEnrollmentRequestRepository;
import com.bidv.coretraining.service.UserService;
import com.bidv.coretraining.service.NotificationService;
import com.bidv.coretraining.service.LessonProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final com.bidv.coretraining.repository.TopicRepository topicRepository;
    private final UserMapper userMapper;
    private final TopicEnrollmentRequestRepository enrollmentRequestRepository;
    private final NotificationService notificationService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final LessonProgressService lessonProgressService;
    private final UserQuizScoreRepository userQuizScoreRepository;

    public UserServiceImpl(UserRepository userRepository, LessonRepository lessonRepository,
                           QuestionRepository questionRepository,
                           com.bidv.coretraining.repository.TopicRepository topicRepository,
                           UserMapper userMapper,
                           TopicEnrollmentRequestRepository enrollmentRequestRepository,
                           NotificationService notificationService,
                           org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                           LessonProgressService lessonProgressService,
                           UserQuizScoreRepository userQuizScoreRepository) {
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.userMapper = userMapper;
        this.enrollmentRequestRepository = enrollmentRequestRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.lessonProgressService = lessonProgressService;
        this.userQuizScoreRepository = userQuizScoreRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .employeeId(user.getEmployeeId())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole())
                .points(user.getPoints())
                .groupId(user.getUserGroup() != null ? user.getUserGroup().getId() : null)
                .groupName(user.getUserGroup() != null ? user.getUserGroup().getName() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserProgressDto getUserProgress(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return buildUserProgressDto(user);
    }

    @Override
    @Transactional
    public UserProgressDto completeLesson(String username, String lessonId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        if (questionRepository.existsByLessonId(lessonId)
                && !hasPassedLessonQuiz(user, lesson)) {
            throw new com.bidv.coretraining.exception.BadRequestException(
                    "Bạn cần đạt tối thiểu " + lesson.getQuizPassingScore()
                            + "% bài kiểm tra của bài học");
        }

        if (!user.getCompletedLessons().contains(lesson)) {
            if (requiresTopicQuizBeforeCourseCompletion(user, lesson)) {
                throw new com.bidv.coretraining.exception.BadRequestException(
                        "Bạn cần hoàn thành bài kiểm tra tổng kết để hoàn thành khóa học");
            }
            user.getCompletedLessons().add(lesson);
            user.setPoints(user.getPoints() + 100);
            user = userRepository.save(user);
        }

        return buildUserProgressDto(user);
    }

    @Override
    @Transactional
    public UserProgressDto resetLesson(String username, String lessonId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        if (user.getCompletedLessons().remove(lesson)) {
            user.setPoints(Math.max(0, user.getPoints() - 100));
            user = userRepository.save(user);
        }
        lessonProgressService.resetProgress(user.getId(), lessonId);
        return buildUserProgressDto(user);
    }

    private boolean hasPassedLessonQuiz(User user, Lesson lesson) {
        return userQuizScoreRepository.findByUserIdAndLessonId(user.getId(), lesson.getId())
                .filter(score -> score.getTotal() != null && score.getTotal() > 0)
                .map(score -> Math.round((float) score.getScore() / score.getTotal() * 100)
                        >= lesson.getQuizPassingScore())
                .orElse(false);
    }

    private boolean requiresTopicQuizBeforeCourseCompletion(User user, Lesson lesson) {
        com.bidv.coretraining.entity.Topic topic = lesson.getTopic();
        if (!questionRepository.existsByTopicIdAndLessonIsNull(topic.getId())
                || hasPassedTopicQuiz(user, topic)) {
            return false;
        }
        return topic.getLessons().stream()
                .allMatch(candidate -> candidate.getId().equals(lesson.getId())
                        || user.getCompletedLessons().stream()
                        .anyMatch(completed -> completed.getId().equals(candidate.getId())));
    }

    private boolean hasPassedTopicQuiz(User user, com.bidv.coretraining.entity.Topic topic) {
        return userQuizScoreRepository
                .findByUserIdAndTopicIdAndLessonIsNull(user.getId(), topic.getId())
                .filter(score -> score.getTotal() != null && score.getTotal() > 0)
                .map(score -> Math.round((float) score.getScore() / score.getTotal() * 100)
                        >= topic.getQuizPassingScore())
                .orElse(false);
    }

    @Override
    @Transactional
    public UserProgressDto registerTopic(String username, String topicId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        com.bidv.coretraining.entity.Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        if (user.getRegisteredTopics().contains(topic)
                || (user.getUserGroup() != null
                && user.getUserGroup().getMandatoryTopics().contains(topic))) {
            return buildUserProgressDto(user);
        }
        if (enrollmentRequestRepository.existsByUserIdAndTopicIdAndStatus(
                user.getId(), topicId, EnrollmentStatus.PENDING)) {
            return buildUserProgressDto(user);
        }
        TopicEnrollmentRequest request = TopicEnrollmentRequest.builder()
                .user(user)
                .topic(topic)
                .status(EnrollmentStatus.PENDING)
                .requestedAt(java.time.LocalDateTime.now())
                .build();
        enrollmentRequestRepository.save(request);
        notificationService.createForAdmins(
                NotificationType.ENROLLMENT_REQUEST,
                "Yêu cầu đăng ký khóa học mới",
                user.getFullName() + " đã yêu cầu đăng ký khóa học " + topic.getName(),
                "/admin?tab=enrollments");

        return buildUserProgressDto(user);
    }

    private UserProgressDto buildUserProgressDto(User user) {
        // Map completed lessons IDs
        java.util.List<String> completedLessons = user.getCompletedLessons().stream()
                .map(Lesson::getId)
                .collect(Collectors.toList());

        // Map quiz scores and totals
        Map<String, Integer> quizScores = new HashMap<>();
        Map<String, Integer> quizTotals = new HashMap<>();
        Map<String, Integer> lessonQuizScores = new HashMap<>();
        Map<String, Integer> lessonQuizTotals = new HashMap<>();
        for (UserQuizScore score : user.getQuizScores()) {
            if (score.getLesson() != null) {
                String lessonId = score.getLesson().getId();
                lessonQuizScores.put(lessonId, score.getScore());
                lessonQuizTotals.put(lessonId, score.getTotal());
            } else {
                String topicId = score.getTopic().getId();
                quizScores.put(topicId, score.getScore());
                quizTotals.put(topicId, score.getTotal());
            }
        }

        String groupId = null;
        String groupName = null;
        java.util.List<String> mandatoryTopics = new ArrayList<>();
        if (user.getUserGroup() != null) {
            groupId = user.getUserGroup().getId();
            groupName = user.getUserGroup().getName();
            mandatoryTopics = user.getUserGroup().getMandatoryTopics().stream()
                    .map(com.bidv.coretraining.entity.Topic::getId)
                    .collect(Collectors.toList());
        }

        java.util.List<String> registeredTopics = user.getRegisteredTopics().stream()
                .map(com.bidv.coretraining.entity.Topic::getId)
                .collect(Collectors.toList());
        java.util.List<String> pendingTopics = enrollmentRequestRepository
                .findByUserIdAndStatus(user.getId(), EnrollmentStatus.PENDING)
                .stream()
                .map(request -> request.getTopic().getId())
                .collect(Collectors.toList());

        return UserProgressDto.builder()
                .completedLessons(completedLessons)
                .quizScores(quizScores)
                .quizTotals(quizTotals)
                .lessonQuizScores(lessonQuizScores)
                .lessonQuizTotals(lessonQuizTotals)
                .lessonProgress(lessonProgressService.getAllProgress(user))
                .points(user.getPoints())
                .unlockedBadges(userMapper.toUserBadgeDtoList(new ArrayList<>(user.getUnlockedBadges())))
                .groupId(groupId)
                .groupName(groupName)
                .mandatoryTopics(mandatoryTopics)
                .registeredTopics(registeredTopics)
                .pendingTopics(pendingTopics)
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Override
    @Transactional
    public void changePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}
