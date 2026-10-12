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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.AuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.AssetAccessService assets;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final CourseMapper courseMapper;
    private final QuizMapper quizMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CourseEnrollmentRequestRepository enrollmentRequestRepository;
    private final NotificationService notificationService;
    private final UserGroupRepository userGroupRepository;

    public AdminServiceImpl(CourseRepository courseRepository, LessonRepository lessonRepository,
                            QuestionRepository questionRepository, CourseMapper courseMapper,
                            QuizMapper quizMapper, UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            CourseEnrollmentRequestRepository enrollmentRequestRepository,
                            NotificationService notificationService,
                            UserGroupRepository userGroupRepository) {
        this.courseRepository = courseRepository;
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


    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.TrainingStructureService structure;
    @org.springframework.beans.factory.annotation.Autowired private ChapterRepository chapterRepository;

    // Course CRUD
    @Override
    @Transactional
    public CourseDto createCourse(CourseDto courseDto) {
        return structure.saveCourse(null,new com.bidv.coretraining.service.TrainingStructureService.CourseRequest(courseDto.getCode(),courseDto.getName(),courseDto.getProgramId(),courseDto.getDescription(),courseDto.getIconName(),courseDto.getVersion()));
    }

    @Override
    @Transactional
    public CourseDto updateCourse(String id, CourseDto courseDto) {
        return structure.saveCourse(id,new com.bidv.coretraining.service.TrainingStructureService.CourseRequest(courseDto.getCode(),courseDto.getName(),courseDto.getProgramId(),courseDto.getDescription(),courseDto.getIconName(),courseDto.getVersion()));
    }

    @Override
    @Transactional
    public void deleteCourse(String id) {
        structure.deleteCourse(id);
    }

    // Lesson CRUD
    @Override
    @Transactional
    public LessonDto createLesson(LessonDto lessonDto) {
        if(lessonDto.getId()==null || lessonDto.getId().isBlank() || lessonDto.getTitle()==null || lessonDto.getTitle().isBlank())
            throw new BadRequestException("Mã và tên bài học không được để trống");
        Chapter chapter=structure.chapter(lessonDto.getChapterId());
        Course course=chapter.getCourse();
        authorization.edit(course);
        if(!"DRAFT".equals(course.getStatus())) throw new BadRequestException("Chuyển khóa về bản nháp trước khi thêm bài");
        if(lessonRepository.existsById(lessonDto.getId())) throw new BadRequestException("Mã bài học đã tồn tại");
        Lesson lesson = Lesson.builder()
                .id(lessonDto.getId())
                .title(lessonDto.getTitle())
                .course(course)
                .chapter(chapter)
                .lessonOrder(chapter.getLessons().stream().mapToInt(Lesson::getLessonOrder).max().orElse(-1)+1)
                .hint(lessonDto.getHint())
                .build();

        lesson.ensureContent();
        assets.attach(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoUrl(normalizeUploadedPath(lessonDto.getVideoUrl()));
        lesson.setVideoSource(resolveVideoSource(lessonDto));
        lesson.setTextBody(lessonDto.getTextBody());
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

        Lesson saved = lessonRepository.saveAndFlush(lesson);
        return courseMapper.toLessonDto(saved);
    }

    @Override
    @Transactional
    public LessonDto updateLesson(String id, LessonDto lessonDto) {
        if(lessonDto.getTitle()==null || lessonDto.getTitle().isBlank())throw new BadRequestException("Tên bài học không được để trống");
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));

        authorization.edit(lesson.getCourse());
        if(!"DRAFT".equals(lesson.getCourse().getStatus())) throw new BadRequestException("Chuyển khóa về bản nháp trước khi sửa nội dung");
        if(lessonDto.getVersion()!=null && !java.util.Objects.equals(lesson.getVersion(),lessonDto.getVersion()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Bài học đã thay đổi. Tải lại trước khi lưu");
        if(lessonDto.getChapterId()!=null && !lesson.getChapter().getId().equals(lessonDto.getChapterId())) throw new BadRequestException("Không đổi chương trong trình soạn nội dung");
        lesson.setTitle(lessonDto.getTitle());
        lesson.setUpdatedAt(java.time.LocalDateTime.now());
        lesson.setHint(lessonDto.getHint());
        lesson.setTextBody(lessonDto.getTextBody());
        assets.attach(normalizeUploadedPath(lessonDto.getVideoUrl()));
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

        Lesson saved = lessonRepository.saveAndFlush(lesson);
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
        structure.deleteLesson(id);
    }

    @Override
    @Transactional
    public List<LessonDto> reorderLessons(String courseId, List<String> lessonIds) {
        authorization.edit(courseId);
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByLessonOrderAscIdAsc(courseId);
        Set<String> existingIds = lessons.stream().map(Lesson::getId).collect(Collectors.toSet());
        if (lessonIds == null
                || lessonIds.size() != lessons.size()
                || Set.copyOf(lessonIds).size() != lessonIds.size()
                || !existingIds.equals(Set.copyOf(lessonIds))) {
            throw new IllegalArgumentException("Danh sách sắp xếp phải chứa đầy đủ và duy nhất các bài học của khóa học");
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

    private int nextLessonOrder(String courseId) {
        return lessonRepository.findByCourseIdOrderByLessonOrderAscIdAsc(courseId).stream()
                .map(Lesson::getLessonOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private void normalizeLessonOrder(String courseId) {
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByLessonOrderAscIdAsc(courseId);
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
            assets.attach(normalizeUploadedPath(documentDto.getFileUrl()));
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
    public List<QuestionDto> getQuizQuestions(String courseId, String lessonId) {
        authorization.edit(courseId);
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }
        if (lessonId != null && !lessonId.isBlank()) {
            Lesson lesson = lessonRepository.findById(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
            if (!lesson.getCourse().getId().equals(courseId)) {
                throw new BadRequestException("Bài học không thuộc khóa học đã chọn");
            }
            return quizMapper.toQuestionDtoList(questionRepository.findByLessonId(lessonId));
        }
        return quizMapper.toQuestionDtoList(questionRepository.findByCourseIdAndLessonIsNull(courseId));
    }

    @Override
    @Transactional
    public QuestionDto createQuestion(QuestionDto questionDto) {
        Course course = courseRepository.findById(questionDto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", questionDto.getCourseId()));

        authorization.edit(questionDto.getCourseId());
        normalizeAndValidateQuestion(questionDto);

        Lesson quizLesson=resolveQuestionLesson(questionDto,course);
        if(quizLesson==null) throw new BadRequestException("Bài kiểm tra phải thuộc một bài học");
        if(!"DRAFT".equals(course.getStatus())) throw new BadRequestException("Chuyển khóa về bản nháp trước khi soạn kiểm tra");
        quizLesson.ensureQuiz();
        lessonRepository.saveAndFlush(quizLesson);
        Question question = Question.builder()
                .quiz(quizLesson.getQuiz())
                .course(course)
                .lesson(resolveQuestionLesson(questionDto, course))
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
        authorization.edit(question.getCourse());

        authorization.edit(questionDto.getCourseId());
        normalizeAndValidateQuestion(questionDto);
        if (!question.getCourse().getId().equals(questionDto.getCourseId())) {
            Course course = courseRepository.findById(questionDto.getCourseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course", "id", questionDto.getCourseId()));
            question.setCourse(course);
        }

        if(!"DRAFT".equals(question.getCourse().getStatus())) throw new BadRequestException("Chuyển khóa về bản nháp trước khi sửa câu hỏi");
        if(!java.util.Objects.equals(question.getLesson().getId(),questionDto.getLessonId())) throw new BadRequestException("Không đổi phạm vi câu hỏi");
        question.setText(questionDto.getText());
        question.setLesson(resolveQuestionLesson(questionDto, question.getCourse()));
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

    private Lesson resolveQuestionLesson(QuestionDto questionDto, Course course) {
        if (questionDto.getLessonId() == null || questionDto.getLessonId().isBlank()) {
            return null;
        }
        Lesson lesson = lessonRepository.findById(questionDto.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", questionDto.getLessonId()));
        if (!lesson.getCourse().getId().equals(course.getId())) {
            throw new IllegalArgumentException("Bài học không thuộc khóa học đã chọn");
        }
        return lesson;
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));
        authorization.edit(question.getCourse());
        if(!"DRAFT".equals(question.getCourse().getStatus()))throw new BadRequestException("Chuyển khóa về bản nháp trước khi xóa câu hỏi");
        questionRepository.delete(question);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRequestDto> getEnrollmentRequests() {
        authorization.permission("ENROLLMENT_MANAGE");
        return (com.bidv.coretraining.security.AccessPolicy.system(authorization.current())?enrollmentRequestRepository.findAllByOrderByRequestedAtDesc():enrollmentRequestRepository.findByUserOrganizationUnitIdInOrderByRequestedAtDesc(authorization.scopeIds())).stream()
                .filter(r -> authorization.allowedScope(r.getUser().getOrganizationUnit()) && com.bidv.coretraining.security.AccessPolicy.visible(r.getUser(),r.getCourse()))
                .map(this::toEnrollmentRequestDto)
                .toList();
    }

    @Override
    @Transactional
    public EnrollmentRequestDto reviewEnrollmentRequest(
            Long id, boolean approve, String rejectionReason, String reviewedBy) {
        CourseEnrollmentRequest request = enrollmentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment request", "id", id));
        authorization.permission("ENROLLMENT_MANAGE");
        authorization.scope(request.getUser().getOrganizationUnit());
        com.bidv.coretraining.security.AccessPolicy.require(com.bidv.coretraining.security.AccessPolicy.visible(request.getUser(),request.getCourse()));
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
            request.getUser().getRegisteredCourses().add(request.getCourse());
            userRepository.save(request.getUser());
        }
        EnrollmentRequestDto result = toEnrollmentRequestDto(enrollmentRequestRepository.save(request));
        notificationService.create(
                request.getUser().getUsername(),
                approve ? NotificationType.ENROLLMENT_APPROVED : NotificationType.ENROLLMENT_REJECTED,
                approve ? "Đăng ký khóa học đã được duyệt" : "Đăng ký khóa học bị từ chối",
                approve
                        ? "Bạn đã được ghi danh vào khóa học " + request.getCourse().getName()
                        : "Khóa học " + request.getCourse().getName()
                            + " bị từ chối. Lý do: " + request.getRejectionReason(),
                approve ? "/" : null);
        return result;
    }

    private EnrollmentRequestDto toEnrollmentRequestDto(CourseEnrollmentRequest request) {
        return EnrollmentRequestDto.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .username(request.getUser().getUsername())
                .fullName(request.getUser().getFullName())
                .courseId(request.getCourse().getId())
                .courseName(request.getCourse().getName())
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
        authorization.edit(settings.getCourseId());
        int passingScore=settings.getPassingScore()==null?80:settings.getPassingScore();
        if(passingScore<1 || passingScore>100)throw new BadRequestException("Ngưỡng đạt phải từ 1 đến 100");
        if(settings.getLessonId()==null)throw new BadRequestException("Kiểm tra phải gắn với bài học");
        Lesson lesson=structure.lesson(settings.getLessonId());
        if(!lesson.getCourse().getId().equals(settings.getCourseId()))throw new BadRequestException("Bài không thuộc khóa");
        authorization.edit(lesson.getCourse());
        if(!"DRAFT".equals(lesson.getCourse().getStatus()))throw new BadRequestException("Chuyển khóa về bản nháp trước khi đổi kiểm tra");
        if(!lesson.hasQuiz())throw new BadRequestException("Thêm ít nhất một câu hỏi trước khi lưu cấu hình bài kiểm tra");
        lesson.getQuiz().setPassingScore(passingScore);
        lesson.getQuiz().setRequiredForProgress(!Boolean.FALSE.equals(settings.getRequiredForProgress()));
        lessonRepository.save(lesson);settings.setPassingScore(passingScore);return settings;
    }

    @Override
    @Transactional
    public void deleteQuiz(String courseId, String lessonId) {
        authorization.edit(courseId);
        if(lessonId==null)throw new BadRequestException("Kiểm tra phải gắn với bài học");
        Lesson lesson=structure.lesson(lessonId);
        if(!lesson.getCourse().getId().equals(courseId))throw new BadRequestException("Bài không thuộc khóa");
        authorization.edit(lesson.getCourse());
        if(!"DRAFT".equals(lesson.getCourse().getStatus()))throw new BadRequestException("Chuyển khóa về bản nháp trước khi xóa kiểm tra");
        questionRepository.deleteByLessonId(lessonId);questionRepository.flush();lesson.setQuiz(null);lessonRepository.save(lesson);
    }

    // User CRUD implementation
    private UserDto mapToUserDto(User user) {
        return UserDto.builder()
                .id(user.getId()).allowedActions(java.util.stream.Stream.of("create","read","update","delete").filter(a->com.bidv.coretraining.security.AccessPolicy.allows(authorization.current(),"users:"+a,user.getOrganizationUnit())).collect(java.util.stream.Collectors.toSet()))
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole()).roles(user.effectiveRoles()).roleNames(com.bidv.coretraining.security.AccessPolicy.roleNames(user)).permissions(com.bidv.coretraining.security.AccessPolicy.permissions(user))
                .unitId(user.getOrganizationUnit()==null?null:user.getOrganizationUnit().getId()).scopeUnitIds(user.getScopeUnitIds()).status(user.getStatus()).learnerType(user.getLearnerType())
                .points(user.getPoints())
                .userGroupId(user.getUserGroup() != null ? user.getUserGroup().getId() : null)
                .employeeId(user.getEmployeeId())
                .email(user.getEmail())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return authorization.manageableUsers().stream()
                .map(this::mapToUserDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) {
        authorization.permission("USER_MANAGE");
        UserGroup userGroup = userDto.getUserGroupId() == null || userDto.getUserGroupId().isBlank()
                ? null : userGroupRepository.findById(userDto.getUserGroupId()).orElseThrow(()->new BadRequestException("Nhóm không tồn tại"));
        String rawPassword = userDto.getPassword() != null && !userDto.getPassword().isBlank()
                ? userDto.getPassword() : java.util.UUID.randomUUID().toString();
        if(rawPassword.length()<8) throw new BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
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
        applyAccess(user,userDto);
        User saved = userRepository.save(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        authorization.user(user);
        user.setFullName(userDto.getFullName());
        user.setAvatarColor(userDto.getAvatarColor());

        user.setPoints(userDto.getPoints());
        UserGroup userGroup = userDto.getUserGroupId() == null || userDto.getUserGroupId().isBlank()
                ? null : userGroupRepository.findById(userDto.getUserGroupId()).orElseThrow(()->new BadRequestException("Nhóm không tồn tại"));
        user.setUserGroup(userGroup);
        user.setEmployeeId(userDto.getEmployeeId());
        user.setEmail(userDto.getEmail());
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            if(userDto.getPassword().length()<8) throw new BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
            user.setMustChangePassword(true);
        }
        applyAccess(user,userDto);
        User saved = userRepository.save(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        authorization.user(user);
        com.bidv.coretraining.security.AccessPolicy.require(!user.getId().equals(authorization.current().getId()));
        userRepository.delete(user);permissionCache.invalidateAfterCommit();
    }

    @Override
    @Transactional
    public void importUsersFromExcel(MultipartFile file) {
        // Legacy import has no unit column; restrict to global admin to avoid unscoped accounts.
        authorization.permission("ORG_MANAGE");
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            Iterator<Row> rows = workbook.getSheetAt(0).iterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                String employeeId = cellValue(row.getCell(1)).trim();
                String email = cellValue(row.getCell(2)).trim();
                String fullName = cellValue(row.getCell(3)).trim();
                String groupText = cellValue(row.getCell(4)).trim().toUpperCase();
                if (employeeId.isEmpty() || email.isEmpty() || fullName.isEmpty()
                        || userRepository.existsByUsername(email)) continue;

                String groupId = groupText.contains("GDV") || groupText.contains("GIAO DỊCH") ? "GDV"
                        : groupText.contains("QHKH") || groupText.contains("QUAN HỆ") ? "QHKH"
                        : groupText.contains("KSV") || groupText.contains("KIỂM SOÁT") ? "KSV" : null;
                UserGroup group = groupId == null ? null : userGroupRepository.findById(groupId).orElse(null);
                String[] colors = {"#005B41", "#4F46E5", "#10B981", "#F59E0B", "#EF4444"};
                userRepository.save(User.builder()
                        .username(email).email(email).employeeId(employeeId).fullName(fullName)
                        .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role("UNASSIGNED").dynamicAccessInitialized(true).points(0).userGroup(group)
                        .avatarColor(colors[Math.abs(employeeId.hashCode()) % colors.length])
                        .mustChangePassword(true)
                        .build());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi khi import dữ liệu Excel: " + exception.getMessage(), exception);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.PermissionCache permissionCache;
    private void applyAccess(User user,UserDto dto) {
        User actor=authorization.current();
        boolean system=com.bidv.coretraining.security.AccessPolicy.system(actor);
        if(user.getId()!=null && dto.getRoles()!=null)com.bidv.coretraining.security.AccessPolicy.require(new java.util.HashSet<>(dto.getRoles()).equals(user.effectiveRoles()));
        OrganizationUnit unit=dto.getUnitId()==null || dto.getUnitId().isBlank()?null:authorization.unit(dto.getUnitId());
        if(!system) authorization.scope(unit);
        if(unit!=null && !com.bidv.coretraining.security.AccessPolicy.activeUnit(unit)) throw new BadRequestException("Đơn vị đã ngừng hoạt động");
        java.util.Set<String> scopes=user.getScopeUnitIds();
        if(dto.getScopeUnitIds()!=null)com.bidv.coretraining.security.AccessPolicy.require(new java.util.HashSet<>(dto.getScopeUnitIds()).equals(scopes));
        if(user.getId()!=null && dto.getRole()!=null)com.bidv.coretraining.security.AccessPolicy.require(dto.getRole().equals(user.getRole()));
        if(!system && user.getId()!=null && user.getId().equals(actor.getId()))
            com.bidv.coretraining.security.AccessPolicy.require(java.util.Objects.equals(unit==null?null:unit.getId(),actor.getOrganizationUnit()==null?null:actor.getOrganizationUnit().getId()));
        String status=dto.getStatus()==null?user.getStatus():dto.getStatus();
        if(user.getId()!=null && java.util.Objects.equals(user.getId(),actor.getId()))com.bidv.coretraining.security.AccessPolicy.require("ACTIVE".equals(status));
        if(com.bidv.coretraining.security.AccessPolicy.system(user))com.bidv.coretraining.security.AccessPolicy.require("ACTIVE".equals(status));
        if(!java.util.Set.of("ACTIVE","LOCKED","DISABLED").contains(status)) throw new BadRequestException("Trạng thái tài khoản không hợp lệ");
        String learnerType=dto.getLearnerType()==null?(user.getLearnerType()==null?"CURRENT_STAFF":user.getLearnerType()):dto.getLearnerType();
        if(!java.util.Set.of("NEW_STAFF","TRANSFERRED_STAFF","CURRENT_STAFF","EXTERNAL").contains(learnerType)) throw new BadRequestException("Đối tượng học viên không hợp lệ");
        if(user.getUserGroup()!=null && user.getUserGroup().getOrganizationUnit()!=null && !com.bidv.coretraining.security.AccessPolicy.ancestors(unit).contains(user.getUserGroup().getOrganizationUnit().getId()))
            throw new BadRequestException("Nhóm không thuộc đơn vị của người dùng");
        if(user.getUserGroup()!=null && !system) authorization.scope(user.getUserGroup().getOrganizationUnit());
        if(user.getId()!=null) user.setTokenVersion(user.getTokenVersion()+1);
        if(!user.isDynamicAccessInitialized()) {
            user.setDynamicAccessInitialized(true);
            user.setAssignedRoles(new java.util.HashSet<>());
            user.setRoles(new java.util.HashSet<>());user.setRole("UNASSIGNED");
        }
        permissionCache.invalidateAfterCommit();
        user.setOrganizationUnit(unit);user.setScopeUnitIds(new java.util.HashSet<>(scopes));user.setStatus(status);user.setLearnerType(learnerType);
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
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Template");
            Row header = sheet.createRow(0);
            String[] labels = {"STT", "Mã nhân viên (*)", "User email (*)", "Tên nhân viên (*)", "Nhóm (GDV / QHKH / KSV)"};
            for (int i = 0; i < labels.length; i++) header.createCell(i).setCellValue(labels[i]);
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue(1);
            sample.createCell(1).setCellValue("NV0982");
            sample.createCell(2).setCellValue("nguyenvanan@bidv.com.vn");
            sample.createCell(3).setCellValue("Nguyễn Văn An");
            sample.createCell(4).setCellValue("GDV");
            for (int i = 0; i < labels.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi khi tạo file Excel mẫu: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserGroup> getAllUserGroups() {
        authorization.permission("GROUP_MANAGE");
        return userGroupRepository.findAll().stream().filter(g->authorization.allowedScope(g.getOrganizationUnit())).toList();
    }
}
