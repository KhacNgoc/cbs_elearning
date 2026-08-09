package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.AdminService;
import com.bidv.coretraining.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.util.CellRangeAddressList;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
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
    private final NotificationService notificationService;
    private final UserGroupRepository userGroupRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.websocket.NotificationWebSocketHandler webSocketHandler;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.repository.UserQuizScoreRepository userQuizScoreRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.repository.LessonProgressRepository lessonProgressRepository;

    public AdminServiceImpl(TopicRepository topicRepository, LessonRepository lessonRepository,
                            QuestionRepository questionRepository, CourseMapper courseMapper,
                            QuizMapper quizMapper, UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            TopicEnrollmentRequestRepository enrollmentRequestRepository,
                            NotificationService notificationService,
                            UserGroupRepository userGroupRepository) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.courseMapper = courseMapper;
        this.quizMapper = quizMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enrollmentRequestRepository = enrollmentRequestRepository;
        this.notificationService = notificationService;
        this.userGroupRepository = userGroupRepository;
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
    @Transactional(readOnly = true)
    public List<QuestionDto> getQuizQuestions(String topicId, String lessonId) {
        if (!topicRepository.existsById(topicId)) {
            throw new ResourceNotFoundException("Topic", "id", topicId);
        }
        if (lessonId != null && !lessonId.isBlank()) {
            Lesson lesson = lessonRepository.findById(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
            if (!lesson.getTopic().getId().equals(topicId)) {
                throw new BadRequestException("Bài học không thuộc chủ đề đã chọn");
            }
            return quizMapper.toQuestionDtoList(questionRepository.findByLessonId(lessonId));
        }
        return quizMapper.toQuestionDtoList(questionRepository.findByTopicIdAndLessonIsNull(topicId));
    }

    @Override
    @Transactional
    public QuestionDto createQuestion(QuestionDto questionDto) {
        Topic topic = topicRepository.findById(questionDto.getTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", questionDto.getTopicId()));

        normalizeAndValidateQuestion(questionDto);

        Question question = Question.builder()
                .topic(topic)
                .lesson(resolveQuestionLesson(questionDto, topic))
                .text(questionDto.getText())
                .options(questionDto.getOptions() != null ? questionDto.getOptions() : new ArrayList<>())
                .correctAnswer(questionDto.getCorrectAnswer())
                .correctAnswers(questionDto.getCorrectAnswers())
                .type(questionDto.getType())
                .build();

        Question saved = questionRepository.save(question);
        return quizMapper.toQuestionDto(saved);
    }

    @Override
    @Transactional
    public QuestionDto updateQuestion(Long id, QuestionDto questionDto) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        normalizeAndValidateQuestion(questionDto);
        if (!question.getTopic().getId().equals(questionDto.getTopicId())) {
            Topic topic = topicRepository.findById(questionDto.getTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", questionDto.getTopicId()));
            question.setTopic(topic);
        }

        question.setText(questionDto.getText());
        question.setLesson(resolveQuestionLesson(questionDto, question.getTopic()));
        question.setOptions(questionDto.getOptions() != null ? questionDto.getOptions() : new ArrayList<>());
        question.setCorrectAnswer(questionDto.getCorrectAnswer());
        question.setCorrectAnswers(questionDto.getCorrectAnswers());
        question.setType(questionDto.getType());

        Question saved = questionRepository.save(question);
        return quizMapper.toQuestionDto(saved);
    }

    private void normalizeAndValidateQuestion(QuestionDto questionDto) {
        if (questionDto.getText() == null || questionDto.getText().isBlank()) {
            throw new BadRequestException("Nội dung câu hỏi không được để trống");
        }
        List<String> options = questionDto.getOptions() == null
                ? List.of() : questionDto.getOptions().stream().map(String::trim).toList();
        if (options.size() < 3 || options.size() > 6 || options.stream().anyMatch(String::isBlank)) {
            throw new BadRequestException("Câu hỏi phải có từ 3 đến 6 lựa chọn hợp lệ");
        }
        if (options.stream().map(String::toLowerCase).distinct().count() != options.size()) {
            throw new BadRequestException("Các lựa chọn không được trùng nhau");
        }
        List<Integer> answers = questionDto.getCorrectAnswers();
        if ((answers == null || answers.isEmpty()) && questionDto.getCorrectAnswer() != null) {
            answers = List.of(questionDto.getCorrectAnswer());
        }
        answers = answers == null ? List.of() : answers.stream()
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (answers.isEmpty() || answers.stream().anyMatch(index -> index < 0 || index >= options.size())) {
            throw new BadRequestException("Đáp án đúng không hợp lệ");
        }
        QuestionType type = questionDto.getType() == null
                ? (answers.size() > 1 ? QuestionType.MULTIPLE_CHOICE : QuestionType.SINGLE_CHOICE)
                : questionDto.getType();
        if (type == QuestionType.SINGLE_CHOICE && answers.size() != 1) {
            throw new BadRequestException("Câu single choice phải có đúng một đáp án đúng");
        }
        if (type == QuestionType.MULTIPLE_CHOICE
                && (answers.size() < 2 || answers.size() >= options.size())) {
            throw new BadRequestException("Câu multiple choice cần ít nhất hai đáp án đúng và không được chọn tất cả");
        }
        questionDto.setText(questionDto.getText().trim());
        questionDto.setOptions(options);
        questionDto.setCorrectAnswers(answers);
        questionDto.setCorrectAnswer(answers.get(0));
        questionDto.setType(type);
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
    public EnrollmentRequestDto reviewEnrollmentRequest(
            Long id, boolean approve, String rejectionReason, String reviewedBy) {
        TopicEnrollmentRequest request = enrollmentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment request", "id", id));
        if (request.getStatus() != EnrollmentStatus.PENDING) {
            throw new BadRequestException("Yêu cầu đăng ký này đã được xử lý");
        }
        if (!approve && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new BadRequestException("Quản trị viên phải nêu lý do từ chối");
        }
        request.setStatus(approve ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        request.setReviewedAt(java.time.LocalDateTime.now());
        request.setReviewedBy(reviewedBy);
        request.setRejectionReason(approve ? null : rejectionReason.trim());
        if (approve) {
            request.getUser().getRegisteredTopics().add(request.getTopic());
            userRepository.save(request.getUser());
        }
        EnrollmentRequestDto result = toEnrollmentRequestDto(enrollmentRequestRepository.save(request));
        notificationService.create(
                request.getUser().getUsername(),
                approve ? NotificationType.ENROLLMENT_APPROVED : NotificationType.ENROLLMENT_REJECTED,
                approve ? "Đăng ký khóa học đã được duyệt" : "Đăng ký khóa học bị từ chối",
                approve
                        ? "Bạn đã được ghi danh vào khóa học " + request.getTopic().getName()
                        : "Khóa học " + request.getTopic().getName()
                            + " bị từ chối. Lý do: " + request.getRejectionReason(),
                approve ? "/" : null);
        return result;
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
                .rejectionReason(request.getRejectionReason())
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
                .userGroupId(user.getUserGroup() != null ? user.getUserGroup().getId() : null)
                .employeeId(user.getEmployeeId())
                .email(user.getEmail())
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
        if (userDto.getUserGroupId() == null || userDto.getUserGroupId().isBlank()) {
            throw new BadRequestException("Nhóm nghiệp vụ là bắt buộc");
        }
        UserGroup userGroup = userGroupRepository.findById(userDto.getUserGroupId())
                .orElseThrow(() -> new BadRequestException("Nhóm nghiệp vụ không hợp lệ"));
        String rawPassword = userDto.getPassword() != null && !userDto.getPassword().isBlank()
                ? userDto.getPassword() : "123456";
        User user = User.builder()
                .username(userDto.getUsername())
                .password(passwordEncoder.encode(rawPassword))
                .fullName(userDto.getFullName())
                .avatarColor(userDto.getAvatarColor() != null && !userDto.getAvatarColor().isBlank()
                        ? userDto.getAvatarColor() : "#005B41")
                .role(userDto.getRole() != null && !userDto.getRole().isBlank()
                        ? userDto.getRole() : "ROLE_USER")
                .points(userDto.getPoints() != null ? userDto.getPoints() : 0)
                .userGroup(userGroup)
                .employeeId(userDto.getEmployeeId())
                .email(userDto.getEmail())
                .mustChangePassword(true)
                .build();
        User saved = userRepository.saveAndFlush(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        if (userDto.getUserGroupId() == null || userDto.getUserGroupId().isBlank()) {
            throw new BadRequestException("Nhóm nghiệp vụ là bắt buộc");
        }
        UserGroup userGroup = userGroupRepository.findById(userDto.getUserGroupId())
                .orElseThrow(() -> new BadRequestException("Nhóm nghiệp vụ không hợp lệ"));
        user.setFullName(userDto.getFullName());
        user.setAvatarColor(userDto.getAvatarColor());
        user.setRole(userDto.getRole());
        user.setPoints(userDto.getPoints());
        user.setUserGroup(userGroup);
        user.setEmployeeId(userDto.getEmployeeId());
        user.setEmail(userDto.getEmail());
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        User saved = userRepository.saveAndFlush(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        userRepository.delete(user);
    }

    @Override
    @Transactional
    public void importUsersFromExcel(MultipartFile file) {
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            Iterator<Row> rows = workbook.getSheetAt(0).iterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                String employeeId = cellValue(row.getCell(1)).trim();
                String email = cellValue(row.getCell(2)).trim();
                String fullName = cellValue(row.getCell(3)).trim();
                String groupText = cellValue(row.getCell(4)).trim();
                if (employeeId.isEmpty() || email.isEmpty() || fullName.isEmpty()
                        || userRepository.existsByUsername(email)) continue;

                UserGroup group = null;
                if (!groupText.isEmpty()) {
                    group = userGroupRepository.findById(groupText.toUpperCase()).orElse(null);
                    if (group == null) {
                        for (UserGroup g : userGroupRepository.findAll()) {
                            if (g.getName().equalsIgnoreCase(groupText)) {
                                group = g;
                                break;
                            }
                        }
                    }
                }

                String[] colors = {"#005B41", "#4F46E5", "#10B981", "#F59E0B", "#EF4444"};
                userRepository.save(User.builder()
                        .username(email).email(email).employeeId(employeeId).fullName(fullName)
                        .password(passwordEncoder.encode("Bidv@" + employeeId.toUpperCase()))
                        .role("ROLE_USER").points(0).userGroup(group)
                        .avatarColor(colors[Math.abs(employeeId.hashCode()) % colors.length])
                        .mustChangePassword(true)
                        .build());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi khi import dữ liệu Excel: " + exception.getMessage(), exception);
        }
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value == (long) value ? String.valueOf((long) value) : String.valueOf(value);
        }
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString();
    }

    @Override
    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Template");

            // Header style for core template (Teal)
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Header style for side reference table (Grey)
            CellStyle refHeaderStyle = workbook.createCellStyle();
            Font refFont = workbook.createFont();
            refFont.setBold(true);
            refFont.setColor(IndexedColors.WHITE.getIndex());
            refHeaderStyle.setFont(refFont);
            refHeaderStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            refHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            refHeaderStyle.setAlignment(HorizontalAlignment.CENTER);

            // Create headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Mã nhân viên (*)", "User email (*)", "Tên nhân viên (*)", "Nhóm nghiệp vụ (Chọn từ danh sách)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create side reference headers
            Cell cellRefId = headerRow.createCell(6); // Column G (index 6)
            cellRefId.setCellValue("Mã Nhóm");
            cellRefId.setCellStyle(refHeaderStyle);

            Cell cellRefName = headerRow.createCell(7); // Column H (index 7)
            cellRefName.setCellValue("Tên Nhóm Nghiệp Vụ");
            cellRefName.setCellStyle(refHeaderStyle);

            // Get groups from repository
            List<UserGroup> groups = userGroupRepository.findAll();
            String[] groupIds;
            java.util.Set<String> uniqueIds = new java.util.LinkedHashSet<>();
            
            if (groups != null && !groups.isEmpty()) {
                for (UserGroup group : groups) {
                    uniqueIds.add(group.getId());
                }
                groupIds = uniqueIds.toArray(new String[0]);
            } else {
                groupIds = new String[]{"CIF", "TIEN_VAY", "CHUYEN_TIEN", "TIEN_GUI", "TELLER", "GL", "THE"};
            }

            // Populate side reference data
            int refRowIdx = 1;
            CellStyle borderStyle = workbook.createCellStyle();
            borderStyle.setBorderBottom(BorderStyle.THIN);
            borderStyle.setBorderTop(BorderStyle.THIN);
            borderStyle.setBorderLeft(BorderStyle.THIN);
            borderStyle.setBorderRight(BorderStyle.THIN);

            if (groups != null && !groups.isEmpty()) {
                for (UserGroup group : groups) {
                    Row refRow = sheet.getRow(refRowIdx);
                    if (refRow == null) {
                        refRow = sheet.createRow(refRowIdx);
                    }
                    Cell idCell = refRow.createCell(6);
                    idCell.setCellValue(group.getId());
                    idCell.setCellStyle(borderStyle);

                    Cell nameCell = refRow.createCell(7);
                    nameCell.setCellValue(group.getName());
                    nameCell.setCellStyle(borderStyle);
                    refRowIdx++;
                }
            } else {
                String[] fallbackNames = {
                    "Nhóm nghiệp vụ CIF", "Nhóm nghiệp vụ Tiền vay", "Nhóm nghiệp vụ Chuyển tiền",
                    "Nhóm nghiệp vụ Tiền gửi", "Nhóm nghiệp vụ Teller", "Nhóm nghiệp vụ GL", "Nhóm nghiệp vụ Thẻ"
                };
                for (int i = 0; i < groupIds.length; i++) {
                    Row refRow = sheet.getRow(refRowIdx);
                    if (refRow == null) {
                        refRow = sheet.createRow(refRowIdx);
                    }
                    Cell idCell = refRow.createCell(6);
                    idCell.setCellValue(groupIds[i]);
                    idCell.setCellStyle(borderStyle);

                    Cell nameCell = refRow.createCell(7);
                    nameCell.setCellValue(fallbackNames[i]);
                    nameCell.setCellStyle(borderStyle);
                    refRowIdx++;
                }
            }

            // Create sample row
            Row sampleRow = sheet.getRow(1);
            if (sampleRow == null) {
                sampleRow = sheet.createRow(1);
            }
            sampleRow.createCell(0).setCellValue(1);
            sampleRow.createCell(1).setCellValue("121212");
            sampleRow.createCell(2).setCellValue("nguyenvanannabel@bidv.com.vn");
            sampleRow.createCell(3).setCellValue("Nguyễn Văn Anna Bel");
            sampleRow.createCell(4).setCellValue("CIF");

            // Setup Data Validation Dropdown for Column E (index 4) from Row 1 to 1000
            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            CellRangeAddressList addressList = new CellRangeAddressList(1, 1000, 4, 4);
            DataValidationConstraint constraint = validationHelper.createExplicitListConstraint(groupIds);
            DataValidation validation = validationHelper.createValidation(constraint, addressList);
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("Lỗi chọn nhóm", "Vui lòng chọn mã nhóm nghiệp vụ chính xác từ danh sách gợi ý!");
            sheet.addValidationData(validation);

            // Auto-size columns A to E, G and H
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.autoSizeColumn(6);
            sheet.autoSizeColumn(7);

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo file Excel mẫu: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGroup> getAllUserGroups() {
        return userGroupRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        long totalUsers = userRepository.count();
        java.util.Set<String> onlineUsernames = webSocketHandler != null ? webSocketHandler.getOnlineUsers() : java.util.Collections.emptySet();
        long activeUsers = onlineUsernames.size();
        long totalLessons = lessonRepository.count();
        long totalQuestions = questionRepository.count();

        // 1. Competency level distribution
        int excellent = 0;
        int good = 0;
        int average = 0;
        int weak = 0;
        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            if ("ROLE_ADMIN".equals(u.getRole())) continue;
            int pts = u.getPoints() != null ? u.getPoints() : 0;
            if (pts >= 2500) excellent++;
            else if (pts >= 1800) good++;
            else if (pts >= 1000) average++;
            else weak++;
        }
        java.util.Map<String, Integer> competencyDistribution = java.util.Map.of(
            "Xuất sắc (>=2500)", excellent,
            "Khá (1800-2499)", good,
            "Trung bình (1000-1799)", average,
            "Yếu (<1000)", weak
        );

        // 2. Average score by topic
        List<Topic> topics = topicRepository.findAll();
        List<UserQuizScore> allScores = userQuizScoreRepository.findAll();
        java.util.Map<String, Double> avgScoreByTopic = new java.util.HashMap<>();
        for (Topic t : topics) {
            double sum = 0;
            int count = 0;
            for (UserQuizScore s : allScores) {
                if (s.getTopic().getId().equals(t.getId())) {
                    double pct = (double) s.getScore() / s.getTotal() * 100;
                    sum += pct;
                    count++;
                }
            }
            if (count > 0) {
                avgScoreByTopic.put(t.getName(), Math.round(sum / count * 10.0) / 10.0);
            } else {
                avgScoreByTopic.put(t.getName(), 0.0);
            }
        }

        // 3. Daily activity (quizzes taken in last 7 days)
        java.time.LocalDateTime sevenDaysAgo = java.time.LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
        java.util.List<UserQuizScore> recentScores = new java.util.ArrayList<>();
        for (UserQuizScore s : allScores) {
            if (s.getTakenAt().isAfter(sevenDaysAgo)) {
                recentScores.add(s);
            }
        }
        // Group by day of week
        java.util.List<DailyActivityDto> dailyActivity = new java.util.ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        // Use Locale.US to guarantee standard English day names for matching
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.US);
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            String dayOfWeekName = d.format(formatter);
            String label = i == 0 ? "Hôm nay" : dayOfWeekName;
            
            if (!"Hôm nay".equals(label)) {
                if (label.contains("Monday")) label = "Thứ 2";
                else if (label.contains("Tuesday")) label = "Thứ 3";
                else if (label.contains("Wednesday")) label = "Thứ 4";
                else if (label.contains("Thursday")) label = "Thứ 5";
                else if (label.contains("Friday")) label = "Thứ 6";
                else if (label.contains("Saturday")) label = "Thứ 7";
                else if (label.contains("Sunday")) label = "Chủ Nhật";
            }

            int count = 0;
            for (UserQuizScore s : recentScores) {
                if (s.getTakenAt().toLocalDate().isEqual(d)) {
                    count++;
                }
            }
            dailyActivity.add(DailyActivityDto.builder().label(label).count(count).build());
        }

        // 4. Active Sessions (Real online users + fallback simulated to make dashboard alive if only 1 user)
        java.util.List<ActiveSessionDto> activeSessions = new java.util.ArrayList<>();
        for (String username : onlineUsernames) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                // Find latest progress
                String action = "Đang trực tuyến";
                String detail = "Đang xem trang tổng quan";
                String topicName = "HỆ THỐNG";

                // Check latest quiz taken
                UserQuizScore latestQuiz = null;
                for (UserQuizScore s : allScores) {
                    if (s.getUser().getId().equals(user.getId())) {
                        if (latestQuiz == null || s.getTakenAt().isAfter(latestQuiz.getTakenAt())) {
                            latestQuiz = s;
                        }
                    }
                }
                if (latestQuiz != null && latestQuiz.getTakenAt().isAfter(java.time.LocalDateTime.now().minusMinutes(30))) {
                    action = "Đang thi thử";
                    detail = "Đề đánh giá năng lực " + latestQuiz.getTopic().getName();
                    topicName = latestQuiz.getTopic().getName();
                } else {
                    // Check latest lesson progress
                    List<LessonProgress> progresses = lessonProgressRepository.findByUserId(user.getId());
                    LessonProgress latestProg = null;
                    for (LessonProgress lp : progresses) {
                        if (latestProg == null || lp.getUpdatedAt().isAfter(latestProg.getUpdatedAt())) {
                            latestProg = lp;
                        }
                    }
                    if (latestProg != null && latestProg.getUpdatedAt().isAfter(java.time.LocalDateTime.now().minusMinutes(30))) {
                        action = "Đang học bài";
                        detail = latestProg.getLesson().getTitle();
                        topicName = latestProg.getLesson().getTopic().getName();
                    }
                }

                activeSessions.add(ActiveSessionDto.builder()
                        .username(username)
                        .fullName(user.getFullName())
                        .avatarColor(user.getAvatarColor() != null ? user.getAvatarColor() : "#005B41")
                        .action(action)
                        .detail(detail)
                        .topic(topicName)
                        .time("Vừa xong")
                        .build());
            }
        }

        return DashboardStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalLessons(totalLessons)
                .totalQuestions(totalQuestions)
                .activeSessions(activeSessions)
                .dailyActivity(dailyActivity)
                .competencyDistribution(competencyDistribution)
                .avgScoreByTopic(avgScoreByTopic)
                .build();
    }
}
