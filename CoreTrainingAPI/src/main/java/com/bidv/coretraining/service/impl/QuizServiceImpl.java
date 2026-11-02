package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.service.LessonProgressService;
import com.bidv.coretraining.service.TrainingStructureService;
import com.bidv.coretraining.mapper.QuizMapper;
import com.bidv.coretraining.enums.QuestionType;

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
import java.util.Map;

@Service
public class QuizServiceImpl implements QuizService {

    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final UserQuizScoreRepository userQuizScoreRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final QuizMapper quizMapper;

    public QuizServiceImpl(QuestionRepository questionRepository, CourseRepository courseRepository,
            LessonRepository lessonRepository,
            UserRepository userRepository, UserQuizScoreRepository userQuizScoreRepository,
            BadgeRepository badgeRepository, UserBadgeRepository userBadgeRepository,
            QuizMapper quizMapper,
            TrainingStructureService structure, LessonProgressService progress, QuizAttemptRepository attempts, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.userQuizScoreRepository = userQuizScoreRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.quizMapper = quizMapper;
        this.structure = structure;
        this.progress = progress;
        this.attempts = attempts;
        this.json = json;
    }

    private final TrainingStructureService structure;
    private final LessonProgressService progress;
    private final QuizAttemptRepository attempts;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionDto> getQuestionsForCourse(String courseId) {
        throw new BadRequestException("Bài kiểm tra thuộc bài học; hãy chọn bài học trong chương");
    }

    @Override
    @Transactional
    public QuizResultDto submitQuiz(String courseId, QuizSubmitRequest request, String username) {
        throw new BadRequestException("Bài kiểm tra thuộc bài học; hãy chọn bài học trong chương");
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionDto> getQuestionsForLesson(String lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }
        structure.requireLesson(lessonId, org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName());
        return questionRepository.findByLessonId(lessonId).stream()
                .map(this::toQuizQuestionDto).toList();
    }

    @Override
    @Transactional
    public QuizResultDto submitLessonQuiz(String lessonId, QuizSubmitRequest request, String username) {
        userRepository.findByUsername(username).ifPresent(u -> userRepository.lockForLearning(u.getId()).orElseThrow());
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        LearningPolicy.assertLessonAccess(user, lesson);
        if (lesson.getQuiz() == null)
            throw new BadRequestException("Bài học chưa có kiểm tra");
        List<Question> questions = questionRepository.findByLessonId(lessonId);
        if (questions.isEmpty())
            throw new BadRequestException("Bài học chưa có câu hỏi kiểm tra");
        Map<Long, List<Integer>> submittedAnswers = request.getSelectedAnswers() == null
                ? Map.of()
                : request.getSelectedAnswers();
        int total = questions.size();
        int score = 0;
        for (Question question : questions) {
            List<Integer> selected = submittedAnswers.get(question.getId());
            if (answersMatch(selected, correctAnswers(question)))
                score++;
        }
        int percent = total == 0 ? 0 : Math.round((float) score / total * 100);
        int passingScore = lesson.getQuizPassingScore();
        Optional<UserQuizScore> existingScore = userQuizScoreRepository.findByUserIdAndLessonId(user.getId(), lessonId);
        if (existingScore.isPresent()) {
            UserQuizScore quizScore = existingScore.get();
            if (quizScore.getTotal() == null || quizScore.getTotal() <= 0
                    || (double) score / total >= (double) quizScore.getScore() / quizScore.getTotal()) {
                quizScore.setScore(score);
                quizScore.setTotal(total);
                quizScore.setTakenAt(LocalDateTime.now());
                saveSelectedAnswers(quizScore, submittedAnswers);
                userQuizScoreRepository.save(quizScore);
            }
        } else if (total > 0) {
            UserQuizScore quizScore = UserQuizScore.builder()
                    .user(user)
                    .course(lesson.getCourse())
                    .lesson(lesson)
                    .score(score)
                    .total(total)
                    .takenAt(LocalDateTime.now())
                    .build();
            saveSelectedAnswers(quizScore, submittedAnswers);
            userQuizScoreRepository.save(quizScore);
            user.getQuizScores().add(quizScore);
        }
        try {
            attempts.save(
                    QuizAttempt.builder().user(user).quiz(lesson.getQuiz()).revision(lesson.getQuiz().getRevision())
                            .score(score).total(total).passingScore(passingScore)
                            .passed(total > 0 && percent >= passingScore)
                            .takenAt(LocalDateTime.now()).answersJson(json.writeValueAsString(submittedAnswers))
                            .questionsJson(
                                    json.writeValueAsString(questions.stream()
                                            .map(q -> Map.of("id", q.getId(), "text", q.getText(), "options",
                                                    q.getOptions(), "correctAnswers", correctAnswers(q)))
                                            .toList()))
                            .build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Không thể lưu đề kiểm tra", e);
        }
        if (total > 0 && percent >= passingScore
                && !user.getCompletedLessons().contains(lesson)
                && lesson.isQuizRequired()
                && progress.contentCompleted(user, lessonId)) {
            progress.recordCompletion(user, lesson);
        }
        return QuizResultDto.builder().score(score).total(total).percent(percent)
                .passingScore(passingScore).passed(total > 0 && percent >= passingScore).build();
    }

    private boolean requiresCourseQuizBeforeCourseCompletion(User user, Lesson lesson) {
        Course course = lesson.getCourse();
        if (!questionRepository.existsByCourseIdAndLessonIsNull(course.getId())
                || hasPassedCourseQuiz(user, course)) {
            return false;
        }
        return course.getLessons().stream()
                .allMatch(candidate -> candidate.getId().equals(lesson.getId())
                        || user.getCompletedLessons().stream()
                                .anyMatch(completed -> completed.getId().equals(candidate.getId())));
    }

    private boolean hasPassedCourseQuiz(User user, Course course) {
        return userQuizScoreRepository
                .findByUserIdAndCourseIdAndLessonIsNull(user.getId(), course.getId())
                .filter(score -> score.getTotal() != null && score.getTotal() > 0)
                .map(score -> Math.round((float) score.getScore() / score.getTotal() * 100) >= course
                        .getQuizPassingScore())
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizReviewDto getCourseQuizReview(String courseId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        LearningPolicy.assertCourseAccess(user, course);
        UserQuizScore score = userQuizScoreRepository
                .findByUserIdAndCourseIdAndLessonIsNull(user.getId(), courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt", "courseId", courseId));
        return buildReview(score, questionRepository.findByCourseIdAndLessonIsNull(courseId),
                course.getQuizPassingScore());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizReviewDto getLessonQuizReview(String lessonId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        LearningPolicy.assertLessonAccess(user, lesson);
        UserQuizScore score = userQuizScoreRepository.findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt", "lessonId", lessonId));
        return buildReview(score, questionRepository.findByLessonId(lessonId), lesson.getQuizPassingScore());
    }

    private QuizReviewDto buildReview(UserQuizScore score, List<Question> questions, int passingScore) {
        int percent = score.getTotal() == 0 ? 0
                : Math.round((float) score.getScore() / score.getTotal() * 100);

        if ((score.getSelectedAnswerSets() == null || score.getSelectedAnswerSets().isEmpty())
                && (score.getSelectedAnswers() == null || score.getSelectedAnswers().isEmpty())) {
            throw new BadRequestException("Lần làm bài cũ chưa có dữ liệu đáp án để xem lại");
        }
        List<QuizReviewQuestionDto> reviewQuestions = questions.stream()
                .map(question -> {
                    List<Integer> selected = storedSelectedAnswers(score, question.getId());
                    List<Integer> correct = correctAnswers(question);
                    return QuizReviewQuestionDto.builder()
                            .id(question.getId())
                            .text(question.getText())
                            .options(question.getOptions())
                            .selectedAnswers(selected)
                            .correctAnswers(correct)
                            .correct(answersMatch(selected, correct))
                            .build();
                })
                .toList();
        return QuizReviewDto.builder()
                .score(score.getScore())
                .total(score.getTotal())
                .percent(percent)
                .passingScore(passingScore)
                .passed(percent >= passingScore)
                .questions(reviewQuestions)
                .build();
    }

    private QuizQuestionDto toQuizQuestionDto(Question question) {
        return QuizQuestionDto.builder()
                .id(question.getId())
                .courseId(question.getCourse().getId())
                .lessonId(question.getLesson() == null ? null : question.getLesson().getId())
                .text(question.getText())
                .options(question.getOptions())
                .type(question.getType() == null ? QuestionType.SINGLE_CHOICE : question.getType())
                .build();
    }

    private List<Integer> correctAnswers(Question question) {
        if (question.getCorrectAnswers() != null && !question.getCorrectAnswers().isEmpty()) {
            return normalizeAnswers(question.getCorrectAnswers());
        }
        return question.getCorrectAnswer() == null
                ? List.of()
                : List.of(question.getCorrectAnswer());
    }

    private List<Integer> normalizeAnswers(List<Integer> answers) {
        if (answers == null)
            return List.of();
        return answers.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    private boolean answersMatch(List<Integer> selected, List<Integer> correct) {
        return !correct.isEmpty() && normalizeAnswers(selected).equals(normalizeAnswers(correct));
    }

    private void saveSelectedAnswers(UserQuizScore score, Map<Long, List<Integer>> answers) {
        Map<Long, String> serialized = new HashMap<>();
        Map<Long, Integer> legacy = new HashMap<>();
        if (answers != null) {
            answers.forEach((questionId, selected) -> {
                List<Integer> normalized = normalizeAnswers(selected);
                serialized.put(questionId, normalized.stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
                if (!normalized.isEmpty())
                    legacy.put(questionId, normalized.get(0));
            });
        }
        score.setSelectedAnswerSets(serialized);
        score.setSelectedAnswers(legacy);
    }

    private List<Integer> storedSelectedAnswers(UserQuizScore score, Long questionId) {
        String serialized = score.getSelectedAnswerSets() == null
                ? null
                : score.getSelectedAnswerSets().get(questionId);
        if (serialized != null) {
            if (serialized.isBlank())
                return List.of();
            try {
                return java.util.Arrays.stream(serialized.split(","))
                        .map(Integer::valueOf).sorted().toList();
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        Integer legacy = score.getSelectedAnswers() == null
                ? null
                : score.getSelectedAnswers().get(questionId);
        return legacy == null ? List.of() : List.of(legacy);
    }

    private void checkAndUnlockBadges(User user, Course course, int score, int total) {
        float ratio = (float) score / total;
        String badgeType = null;
        String badgeTitle = "";
        String badgeIcon = "";
        int badgePoints = 0;

        if (ratio >= 1.0f) {
            badgeType = "gold";
            badgeTitle = "Huy hiệu Vàng " + course.getName();
            badgeIcon = "Award";
            badgePoints = 300;
        } else if (ratio > 0.9f) {
            badgeType = "silver";
            badgeTitle = "Huy hiệu Bạc " + course.getName();
            badgeIcon = "Medal";
            badgePoints = 200;
        } else if (ratio >= 0.8f) {
            badgeType = "bronze";
            badgeTitle = "Huy hiệu Đồng " + course.getName();
            badgeIcon = "Shield";
            badgePoints = 100;
        }

        if (badgeType != null) {
            String badgeId = course.getId() + "_" + badgeType;
            final String finalBadgeTitle = badgeTitle;
            final String finalBadgeIcon = badgeIcon;

            // Check if Badge exists in database, if not create it
            Badge badge = badgeRepository.findById(badgeId).orElseGet(() -> {
                Badge newBadge = Badge.builder()
                        .id(badgeId)
                        .title(finalBadgeTitle)
                        .type(badgeId.contains("gold") ? "gold" : (badgeId.contains("silver") ? "silver" : "bronze"))
                        .course(course)
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
