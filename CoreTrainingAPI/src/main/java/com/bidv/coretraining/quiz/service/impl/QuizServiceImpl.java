package com.bidv.coretraining.quiz.service.impl;

import com.bidv.coretraining.enums.QuestionType;
import com.bidv.coretraining.lesson.service.LessonProgressService;
import com.bidv.coretraining.mapper.QuizMapper;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.training.service.TrainingStructureService;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.leaderboard.entity.Badge;
import com.bidv.coretraining.leaderboard.entity.UserBadge;
import com.bidv.coretraining.leaderboard.repository.BadgeRepository;
import com.bidv.coretraining.leaderboard.repository.UserBadgeRepository;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.quiz.dto.QuizQuestionDto;
import com.bidv.coretraining.quiz.dto.QuizResultDto;
import com.bidv.coretraining.quiz.dto.QuizReviewDto;
import com.bidv.coretraining.quiz.dto.QuizReviewQuestionDto;
import com.bidv.coretraining.quiz.dto.QuizSubmitRequest;
import com.bidv.coretraining.quiz.entity.Question;
import com.bidv.coretraining.quiz.entity.QuizAttempt;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import com.bidv.coretraining.quiz.repository.QuestionRepository;
import com.bidv.coretraining.quiz.repository.QuizAttemptRepository;
import com.bidv.coretraining.quiz.repository.UserQuizScoreRepository;
import com.bidv.coretraining.quiz.service.QuizService;
import com.bidv.coretraining.quiz.service.QuizSessionService;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
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
            TrainingStructureService structure, LessonProgressService progress, QuizAttemptRepository attempts, com.fasterxml.jackson.databind.ObjectMapper json,
            QuizSessionService quizSessions) {
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
        this.quizSessions = quizSessions;
    }

    private final TrainingStructureService structure;
    private final LessonProgressService progress;
    private final QuizAttemptRepository attempts;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final QuizSessionService quizSessions;

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
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
        structure.requireLesson(lessonId, username);
        List<Question> questions = questionRepository.findByLessonIdOrderByQuestionOrderAscIdAsc(lessonId);
        var quiz = lessonRepository.findById(lessonId).map(Lesson::getQuiz).orElse(null);
        Long userId = userRepository.findByUsername(username).map(User::getId).orElse(0L);
        Map<Long, Question> byId = questions.stream()
                .collect(java.util.stream.Collectors.toMap(Question::getId, q -> q));
        return layoutFor(quiz, userId, questions).stream().map(slot -> {
            QuizQuestionDto dto = toQuizQuestionDto(byId.get(slot.questionId()));
            dto.setOptionIndexes(slot.optionOrder());
            dto.setOptions(slot.optionOrder().stream().map(dto.getOptions()::get).toList());
            return dto;
        }).toList();
    }

    /**
     * One question as a learner sees it: {@code optionOrder} lists the original option indexes in display
     * order. Answers are always submitted and graded with original indexes.
     */
    record LayoutSlot(Long questionId, List<Integer> optionOrder) {}

    /**
     * The order this learner sees in their current attempt. Seeded by learner, quiz, attempt number and quiz
     * revision: reloading during an attempt (and grading its submission) gives the same layout, while each
     * new attempt and each learner gets a different one. Without shuffle settings it is the editor's order.
     */
    private List<LayoutSlot> layoutFor(com.bidv.coretraining.quiz.entity.Quiz quiz, Long userId,
            List<Question> questions) {
        boolean shuffleQuestions = quiz != null && quiz.isShuffleQuestions();
        boolean shuffleOptions = quiz != null && quiz.isShuffleOptions();
        java.util.Random random = shuffleQuestions || shuffleOptions
                ? new java.util.Random(java.util.Objects.hash(userId, quiz.getId(),
                        attempts.countByUserIdAndQuizId(userId, quiz.getId()), quiz.getRevision()))
                : null;
        List<Question> ordered = new java.util.ArrayList<>(questions);
        if (shuffleQuestions)
            java.util.Collections.shuffle(ordered, random);
        return ordered.stream().map(question -> {
            List<Integer> order = new java.util.ArrayList<>(
                    java.util.stream.IntStream.range(0, question.getOptions().size()).boxed().toList());
            if (shuffleOptions)
                java.util.Collections.shuffle(order, random);
            return new LayoutSlot(question.getId(), List.copyOf(order));
        }).toList();
    }

    /** Original option index to the position the learner saw it in. */
    private static List<Integer> toDisplay(List<Integer> originalIndexes, List<Integer> optionOrder) {
        return originalIndexes.stream().map(optionOrder::indexOf).filter(index -> index >= 0).sorted().toList();
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
        List<Question> questions = questionRepository.findByLessonIdOrderByQuestionOrderAscIdAsc(lessonId);
        if (questions.isEmpty())
            throw new BadRequestException("Bài học chưa có câu hỏi kiểm tra");
        quizSessions.consume(user, lesson, request.getSessionId());
        // Same seed as when the questions were served: the attempt is not recorded yet.
        List<LayoutSlot> layout = layoutFor(lesson.getQuiz(), user.getId(), questions);
        String layoutJson = writeJson(layout);
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
                quizScore.setQuestionLayout(layoutJson);
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
                    .questionLayout(layoutJson)
                    .build();
            saveSelectedAnswers(quizScore, submittedAnswers);
            userQuizScoreRepository.save(quizScore);
            user.getQuizScores().add(quizScore);
        }
        // The snapshot records the exam exactly as this learner saw it: question order, option order, and
        // answers as display positions, so reports read it like the page the learner answered.
        Map<Long, Question> byId = questions.stream()
                .collect(java.util.stream.Collectors.toMap(Question::getId, q -> q));
        Map<Long, List<Integer>> displayedAnswers = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> snapshot = layout.stream().map(slot -> {
            Question q = byId.get(slot.questionId());
            List<Integer> selected = submittedAnswers.get(q.getId());
            if (selected != null)
                displayedAnswers.put(q.getId(), toDisplay(normalizeAnswers(selected), slot.optionOrder()));
            return Map.<String, Object>of("id", q.getId(), "text", q.getText(),
                    "options", slot.optionOrder().stream().map(q.getOptions()::get).toList(),
                    "correctAnswers", toDisplay(correctAnswers(q), slot.optionOrder()));
        }).toList();
        attempts.save(
                QuizAttempt.builder().user(user).quiz(lesson.getQuiz()).revision(lesson.getQuiz().getRevision())
                        .score(score).total(total).passingScore(passingScore)
                        .passed(total > 0 && percent >= passingScore)
                        .takenAt(LocalDateTime.now()).answersJson(writeJson(displayedAnswers))
                        .questionsJson(writeJson(snapshot))
                        .build());
        // Passing a required quiz completes the lesson on its own: the quiz is what proves the learning.
        if (total > 0 && percent >= passingScore
                && !user.getCompletedLessons().contains(lesson)
                && lesson.isQuizRequired()) {
            progress.recordCompletion(user, lesson);
        }
        awardCourseBadge(user, lesson.getCourse());
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
        return buildReview(score, questionRepository.findByCourseIdAndLessonIsNullOrderByQuestionOrderAscIdAsc(courseId),
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
        return buildReview(score, questionRepository.findByLessonIdOrderByQuestionOrderAscIdAsc(lessonId), lesson.getQuizPassingScore());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Không thể lưu đề kiểm tra", e);
        }
    }

    private static boolean isPermutation(List<Integer> order, int size) {
        return order.size() == size && new java.util.HashSet<>(order).size() == size
                && order.stream().allMatch(index -> index != null && index >= 0 && index < size);
    }

    /** Stored layout of a result; empty for results recorded before layouts were kept. */
    private List<LayoutSlot> readLayout(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank())
            return List.of();
        try {
            return json.readValue(layoutJson, new com.fasterxml.jackson.core.type.TypeReference<List<LayoutSlot>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
    }

    private QuizReviewDto buildReview(UserQuizScore score, List<Question> questions, int passingScore) {
        int percent = score.getTotal() == 0 ? 0
                : Math.round((float) score.getScore() / score.getTotal() * 100);

        if ((score.getSelectedAnswerSets() == null || score.getSelectedAnswerSets().isEmpty())
                && (score.getSelectedAnswers() == null || score.getSelectedAnswers().isEmpty())) {
            throw new BadRequestException("Lần làm bài cũ chưa có dữ liệu đáp án để xem lại");
        }
        // Replay the order the learner saw. Questions added since, or whose options changed, fall back to
        // the editor's order after the recorded ones.
        Map<Long, List<Integer>> optionOrders = new java.util.LinkedHashMap<>();
        readLayout(score.getQuestionLayout()).forEach(slot -> optionOrders.put(slot.questionId(), slot.optionOrder()));
        Map<Long, Integer> position = new HashMap<>();
        optionOrders.keySet().forEach(id -> position.put(id, position.size()));
        List<QuizReviewQuestionDto> reviewQuestions = questions.stream()
                .sorted(java.util.Comparator.comparingInt(q -> position.getOrDefault(q.getId(), Integer.MAX_VALUE)))
                .map(question -> {
                    boolean graded = answersMatch(storedSelectedAnswers(score, question.getId()), correctAnswers(question));
                    List<Integer> order = optionOrders.get(question.getId());
                    if (order == null || !isPermutation(order, question.getOptions().size()))
                        order = java.util.stream.IntStream.range(0, question.getOptions().size()).boxed().toList();
                    return QuizReviewQuestionDto.builder()
                            .id(question.getId())
                            .text(question.getText())
                            .options(order.stream().map(question.getOptions()::get).toList())
                            .selectedAnswers(toDisplay(storedSelectedAnswers(score, question.getId()), order))
                            .correctAnswers(toDisplay(correctAnswers(question), order))
                            .correct(graded)
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
                .options(List.copyOf(question.getOptions()))
                .optionIndexes(java.util.stream.IntStream.range(0, question.getOptions().size()).boxed().toList())
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

    // A course badge needs every lesson quiz attempted, and is graded on the best score of each.
    private void awardCourseBadge(User user, Course course) {
        var quizLessons = course.getLessons().stream()
                .filter(l -> l.getQuiz() != null && "PUBLISHED".equals(l.getStatus())
                        && questionRepository.existsByLessonId(l.getId()))
                .map(Lesson::getId).collect(java.util.stream.Collectors.toSet());
        var best = userQuizScoreRepository.findByUserId(user.getId()).stream()
                .filter(s -> s.getLesson() != null && quizLessons.contains(s.getLesson().getId())
                        && s.getTotal() != null && s.getTotal() > 0)
                .toList();
        if (quizLessons.isEmpty() || best.size() != quizLessons.size())
            return;
        checkAndUnlockBadges(user, course, best.stream().mapToInt(UserQuizScore::getScore).sum(),
                best.stream().mapToInt(UserQuizScore::getTotal).sum());
    }

    private void checkAndUnlockBadges(User user, Course course, int score, int total) {
        double ratio = (double) score / total;
        String badgeType = null;
        String badgeTitle = "";
        String badgeIcon = "";
        int badgePoints = 0;

        if (ratio >= 1.0) {
            badgeType = "gold";
            badgeTitle = "Huy hiệu Vàng " + course.getName();
            badgeIcon = "Award";
            badgePoints = 300;
        } else if (ratio > 0.9) {
            badgeType = "silver";
            badgeTitle = "Huy hiệu Bạc " + course.getName();
            badgeIcon = "Medal";
            badgePoints = 200;
        } else if (ratio >= 0.8) {
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
