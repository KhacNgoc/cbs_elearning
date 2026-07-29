package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.AdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final CourseMapper courseMapper;
    private final QuizMapper quizMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TopicEnrollmentRequestRepository enrollmentRequestRepository;

    public AdminServiceImpl(TopicRepository topicRepository, LessonRepository lessonRepository,
                            QuestionRepository questionRepository, CourseMapper courseMapper,
                            QuizMapper quizMapper, UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            TopicEnrollmentRequestRepository enrollmentRequestRepository) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.courseMapper = courseMapper;
        this.quizMapper = quizMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enrollmentRequestRepository = enrollmentRequestRepository;
    }


    // Topic CRUD
    @Override
    @Transactional
    public TopicDto createTopic(TopicDto topicDto) {
        Topic topic = Topic.builder()
                .id(topicDto.getId())
                .name(topicDto.getName())
                .iconName(topicDto.getIconName())
                .build();
        Topic saved = topicRepository.save(topic);
        return courseMapper.toTopicDto(saved);
    }

    @Override
    @Transactional
    public TopicDto updateTopic(String id, TopicDto topicDto) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", id));
        topic.setName(topicDto.getName());
        topic.setIconName(topicDto.getIconName());
        Topic saved = topicRepository.save(topic);
        return courseMapper.toTopicDto(saved);
    }

    @Override
    @Transactional
    public void deleteTopic(String id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", id));
        topicRepository.delete(topic);
    }

    // Lesson CRUD
    @Override
    @Transactional
    public LessonDto createLesson(LessonDto lessonDto) {
        Topic topic = topicRepository.findById(lessonDto.getTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", lessonDto.getTopicId()));

        Lesson lesson = Lesson.builder()
                .id(lessonDto.getId())
                .title(lessonDto.getTitle())
                .topic(topic)
                .lessonOrder(nextLessonOrder(topic.getId()))
                .hint(lessonDto.getHint())
                .videoUrl(normalizeUploadedPath(lessonDto.getVideoUrl()))
                .videoSource(resolveVideoSource(lessonDto))
                .build();

        lesson.setDocuments(buildDocuments(lessonDto.getDocuments(), lesson));

        if (lessonDto.getProcedureSteps() != null) {
            List<ProcedureSection> sections = new ArrayList<>();
            for (int i = 0; i < lessonDto.getProcedureSteps().size(); i++) {
                ProcedureSectionDto secDto = lessonDto.getProcedureSteps().get(i);
                sections.add(ProcedureSection.builder()
                        .title(secDto.getTitle())
                        .steps(secDto.getSteps() != null ? secDto.getSteps() : new ArrayList<>())
                        .lesson(lesson)
                        .sectionOrder(i)
                        .build());
            }
            lesson.setProcedureSteps(sections);
        }

        Lesson saved = lessonRepository.save(lesson);
        return courseMapper.toLessonDto(saved);
    }

    @Override
    @Transactional
    public LessonDto updateLesson(String id, LessonDto lessonDto) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));

        String previousTopicId = lesson.getTopic().getId();
        if (!lesson.getTopic().getId().equals(lessonDto.getTopicId())) {
            Topic topic = topicRepository.findById(lessonDto.getTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", lessonDto.getTopicId()));
            lesson.setTopic(topic);
            lesson.setLessonOrder(nextLessonOrder(topic.getId()));
        }

        lesson.setTitle(lessonDto.getTitle());
        lesson.setHint(lessonDto.getHint());
        lesson.setVideoUrl(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoSource(resolveVideoSource(lessonDto));
        lesson.getDocuments().clear();
        lesson.getDocuments().addAll(buildDocuments(lessonDto.getDocuments(), lesson));

        // Rebuild procedure steps to avoid merge errors
        lesson.getProcedureSteps().clear();
        if (lessonDto.getProcedureSteps() != null) {
            for (int i = 0; i < lessonDto.getProcedureSteps().size(); i++) {
                ProcedureSectionDto secDto = lessonDto.getProcedureSteps().get(i);
                lesson.getProcedureSteps().add(ProcedureSection.builder()
                        .title(secDto.getTitle())
                        .steps(secDto.getSteps() != null ? secDto.getSteps() : new ArrayList<>())
                        .lesson(lesson)
                        .sectionOrder(i)
                        .build());
            }
        }

        Lesson saved = lessonRepository.save(lesson);
        if (!previousTopicId.equals(saved.getTopic().getId())) {
            lessonRepository.flush();
            normalizeLessonOrder(previousTopicId);
        }
        return courseMapper.toLessonDto(saved);
    }

    private VideoSource resolveVideoSource(LessonDto lessonDto) {
        if (lessonDto.getVideoSource() != null) {
            return lessonDto.getVideoSource();
        }
        String videoUrl = lessonDto.getVideoUrl();
        return videoUrl != null && videoUrl.contains("/uploads/")
                ? VideoSource.UPLOAD
                : VideoSource.URL;
    }

    private String normalizeUploadedPath(String value) {
        if (value == null) return null;
        int uploadsIndex = value.indexOf("/uploads/");
        return uploadsIndex > 0 ? value.substring(uploadsIndex) : value;
    }

    @Override
    @Transactional
    public void deleteLesson(String id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        String topicId = lesson.getTopic().getId();
        lessonRepository.delete(lesson);
        lessonRepository.flush();
        normalizeLessonOrder(topicId);
    }

    @Override
    @Transactional
    public List<LessonDto> reorderLessons(String topicId, List<String> lessonIds) {
        if (!topicRepository.existsById(topicId)) {
            throw new ResourceNotFoundException("Topic", "id", topicId);
        }
        List<Lesson> lessons = lessonRepository.findByTopicIdOrderByLessonOrderAscIdAsc(topicId);
        Set<String> existingIds = lessons.stream().map(Lesson::getId).collect(Collectors.toSet());
        if (lessonIds == null
                || lessonIds.size() != lessons.size()
                || Set.copyOf(lessonIds).size() != lessonIds.size()
                || !existingIds.equals(Set.copyOf(lessonIds))) {
            throw new IllegalArgumentException("Danh sách sắp xếp phải chứa đầy đủ và duy nhất các bài học của chủ đề");
        }

        java.util.Map<String, Lesson> lessonsById = lessons.stream()
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));
        for (int index = 0; index < lessonIds.size(); index++) {
            lessonsById.get(lessonIds.get(index)).setLessonOrder(index);
        }
        return courseMapper.toLessonDtoList(
                lessonRepository.saveAll(
                        lessonIds.stream().map(lessonsById::get).toList()
                )
        );
    }

    private int nextLessonOrder(String topicId) {
        return lessonRepository.findByTopicIdOrderByLessonOrderAscIdAsc(topicId).stream()
                .map(Lesson::getLessonOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private void normalizeLessonOrder(String topicId) {
        List<Lesson> lessons = lessonRepository.findByTopicIdOrderByLessonOrderAscIdAsc(topicId);
        for (int index = 0; index < lessons.size(); index++) {
            lessons.get(index).setLessonOrder(index);
        }
        lessonRepository.saveAll(lessons);
    }

    private List<LessonDocument> buildDocuments(List<LessonDocumentDto> documentDtos, Lesson lesson) {
        if (documentDtos == null) {
            return new ArrayList<>();
        }

        List<LessonDocument> documents = new ArrayList<>();
        for (int i = 0; i < documentDtos.size(); i++) {
            LessonDocumentDto documentDto = documentDtos.get(i);
            documents.add(LessonDocument.builder()
                    .name(documentDto.getName())
                    .documentType(documentDto.getDocumentType())
                    .fileUrl(normalizeUploadedPath(documentDto.getFileUrl()))
                    .originalFileName(documentDto.getOriginalFileName())
                    .fileSize(documentDto.getFileSize())
                    .documentOrder(i)
                    .lesson(lesson)
                    .build());
        }
        return documents;
    }

    // Question CRUD
    @Override
    @Transactional
    public QuestionDto createQuestion(QuestionDto questionDto) {
        Topic topic = topicRepository.findById(questionDto.getTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", questionDto.getTopicId()));

        Question question = Question.builder()
                .topic(topic)
                .lesson(resolveQuestionLesson(questionDto, topic))
                .text(questionDto.getText())
                .options(questionDto.getOptions() != null ? questionDto.getOptions() : new ArrayList<>())
                .correctAnswer(questionDto.getCorrectAnswer())
                .build();

        Question saved = questionRepository.save(question);
        return quizMapper.toQuestionDto(saved);
    }

    @Override
    @Transactional
    public QuestionDto updateQuestion(Long id, QuestionDto questionDto) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        if (!question.getTopic().getId().equals(questionDto.getTopicId())) {
            Topic topic = topicRepository.findById(questionDto.getTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", questionDto.getTopicId()));
            question.setTopic(topic);
        }

        question.setText(questionDto.getText());
        question.setLesson(resolveQuestionLesson(questionDto, question.getTopic()));
        question.setOptions(questionDto.getOptions() != null ? questionDto.getOptions() : new ArrayList<>());
        question.setCorrectAnswer(questionDto.getCorrectAnswer());

        Question saved = questionRepository.save(question);
        return quizMapper.toQuestionDto(saved);
    }

    private Lesson resolveQuestionLesson(QuestionDto questionDto, Topic topic) {
        if (questionDto.getLessonId() == null || questionDto.getLessonId().isBlank()) {
            return null;
        }
        Lesson lesson = lessonRepository.findById(questionDto.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", questionDto.getLessonId()));
        if (!lesson.getTopic().getId().equals(topic.getId())) {
            throw new IllegalArgumentException("Bài học không thuộc chủ đề đã chọn");
        }
        return lesson;
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));
        questionRepository.delete(question);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRequestDto> getEnrollmentRequests() {
        return enrollmentRequestRepository.findAllByOrderByRequestedAtDesc().stream()
                .map(this::toEnrollmentRequestDto)
                .toList();
    }

    @Override
    @Transactional
    public EnrollmentRequestDto reviewEnrollmentRequest(Long id, boolean approve, String reviewedBy) {
        TopicEnrollmentRequest request = enrollmentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment request", "id", id));
        request.setStatus(approve ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        request.setReviewedAt(java.time.LocalDateTime.now());
        request.setReviewedBy(reviewedBy);
        if (approve) {
            request.getUser().getRegisteredTopics().add(request.getTopic());
            userRepository.save(request.getUser());
        }
        return toEnrollmentRequestDto(enrollmentRequestRepository.save(request));
    }

    private EnrollmentRequestDto toEnrollmentRequestDto(TopicEnrollmentRequest request) {
        return EnrollmentRequestDto.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .username(request.getUser().getUsername())
                .fullName(request.getUser().getFullName())
                .topicId(request.getTopic().getId())
                .topicName(request.getTopic().getName())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewedBy(request.getReviewedBy())
                .build();
    }

    @Override
    @Transactional
    public QuizSettingsDto updateQuizSettings(QuizSettingsDto settings) {
        int passingScore = settings.getPassingScore() == null ? 80 : settings.getPassingScore();
        if (passingScore < 1 || passingScore > 100) {
            throw new IllegalArgumentException("Ngưỡng đạt phải nằm trong khoảng từ 1 đến 100");
        }
        if (settings.getLessonId() != null && !settings.getLessonId().isBlank()) {
            Lesson lesson = lessonRepository.findById(settings.getLessonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", settings.getLessonId()));
            if (!lesson.getTopic().getId().equals(settings.getTopicId())) {
                throw new IllegalArgumentException("Bài học không thuộc chủ đề đã chọn");
            }
            lesson.setQuizPassingScore(passingScore);
            lessonRepository.save(lesson);
        } else {
            Topic topic = topicRepository.findById(settings.getTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", settings.getTopicId()));
            topic.setQuizPassingScore(passingScore);
            topicRepository.save(topic);
        }
        settings.setPassingScore(passingScore);
        return settings;
    }

    @Override
    @Transactional
    public void deleteQuiz(String topicId, String lessonId) {
        if (lessonId != null && !lessonId.isBlank()) {
            Lesson lesson = lessonRepository.findById(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
            if (!lesson.getTopic().getId().equals(topicId)) {
                throw new IllegalArgumentException("Bài học không thuộc chủ đề đã chọn");
            }
            questionRepository.deleteByLessonId(lessonId);
        } else {
            if (!topicRepository.existsById(topicId)) {
                throw new ResourceNotFoundException("Topic", "id", topicId);
            }
            questionRepository.deleteByTopicIdAndLessonIsNull(topicId);
        }
    }

    // User CRUD implementation
    private UserDto mapToUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole())
                .points(user.getPoints())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) {
        User user = User.builder()
                .username(userDto.getUsername())
                .password(passwordEncoder.encode(userDto.getPassword() != null ? userDto.getPassword() : "123456"))
                .fullName(userDto.getFullName())
                .avatarColor(userDto.getAvatarColor() != null ? userDto.getAvatarColor() : "#005B41")
                .role(userDto.getRole() != null ? userDto.getRole() : "ROLE_USER")
                .points(userDto.getPoints() != null ? userDto.getPoints() : 0)
                .build();
        User saved = userRepository.save(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        user.setFullName(userDto.getFullName());
        user.setAvatarColor(userDto.getAvatarColor());
        user.setRole(userDto.getRole());
        user.setPoints(userDto.getPoints());
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        User saved = userRepository.save(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        userRepository.delete(user);
    }
}
