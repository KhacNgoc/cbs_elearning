package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.AdminService;
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
    private final UserGroupRepository userGroupRepository;

    public AdminServiceImpl(TopicRepository topicRepository, LessonRepository lessonRepository,
                            QuestionRepository questionRepository, CourseMapper courseMapper,
                            QuizMapper quizMapper, UserRepository userRepository,
                            PasswordEncoder passwordEncoder, UserGroupRepository userGroupRepository) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.courseMapper = courseMapper;
        this.quizMapper = quizMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        if (!lesson.getTopic().getId().equals(lessonDto.getTopicId())) {
            Topic topic = topicRepository.findById(lessonDto.getTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", lessonDto.getTopicId()));
            lesson.setTopic(topic);
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
        lessonRepository.delete(lesson);
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
        UserGroup userGroup = null;
        if (userDto.getUserGroupId() != null && !userDto.getUserGroupId().trim().isEmpty()) {
            userGroup = userGroupRepository.findById(userDto.getUserGroupId()).orElse(null);
        }
        User user = User.builder()
                .username(userDto.getUsername())
                .password(passwordEncoder.encode(userDto.getPassword() != null ? userDto.getPassword() : "123456"))
                .fullName(userDto.getFullName())
                .avatarColor(userDto.getAvatarColor() != null ? userDto.getAvatarColor() : "#005B41")
                .role(userDto.getRole() != null ? userDto.getRole() : "ROLE_USER")
                .points(userDto.getPoints() != null ? userDto.getPoints() : 0)
                .userGroup(userGroup)
                .employeeId(userDto.getEmployeeId())
                .email(userDto.getEmail())
                .mustChangePassword(true)
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
        
        UserGroup userGroup = null;
        if (userDto.getUserGroupId() != null && !userDto.getUserGroupId().trim().isEmpty()) {
            userGroup = userGroupRepository.findById(userDto.getUserGroupId()).orElse(null);
        }
        user.setUserGroup(userGroup);
        user.setEmployeeId(userDto.getEmployeeId());
        user.setEmail(userDto.getEmail());
        
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

    @Override
    @Transactional
    public void importUsersFromExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header row
            if (rows.hasNext()) {
                rows.next();
            }

            while (rows.hasNext()) {
                Row row = rows.next();
                
                // Get cells
                Cell cellEmployeeId = row.getCell(1);
                Cell cellEmail = row.getCell(2);
                Cell cellFullName = row.getCell(3);
                Cell cellGroup = row.getCell(4);

                if (cellEmployeeId == null || cellEmail == null || cellFullName == null) {
                    continue; // Skip empty/invalid rows
                }

                String employeeId = getCellValueAsString(cellEmployeeId).trim();
                String email = getCellValueAsString(cellEmail).trim();
                String fullName = getCellValueAsString(cellFullName).trim();
                String groupText = cellGroup != null ? getCellValueAsString(cellGroup).trim() : "";

                if (employeeId.isEmpty() || email.isEmpty() || fullName.isEmpty()) {
                    continue; // Skip if mandatory values are empty
                }

                // Check if user already exists (use email as username)
                if (userRepository.existsByUsername(email)) {
                    continue; // Skip existing user to avoid duplication
                }

                // Resolve UserGroup
                UserGroup userGroup = null;
                if (!groupText.isEmpty()) {
                    String groupCode = groupText.toUpperCase().trim();
                    userGroup = userGroupRepository.findById(groupCode).orElse(null);
                    if (userGroup == null) {
                        List<UserGroup> allGroups = userGroupRepository.findAll();
                        for (UserGroup g : allGroups) {
                            String gId = g.getId().toUpperCase();
                            String gName = g.getName().toUpperCase();
                            if (groupCode.equals(gId) || groupCode.equals(gName) || 
                                gName.contains(groupCode) || groupCode.contains(gName) ||
                                gId.replace("_", "").contains(groupCode.replace("_", "")) ||
                                groupCode.replace("_", "").contains(gId.replace("_", ""))) {
                                userGroup = g;
                                break;
                            }
                        }
                    }
                }

                // Initial password is: Bidv@ + Employee ID (in uppercase)
                String initialPassword = "Bidv@" + employeeId.toUpperCase();
                String encodedPassword = passwordEncoder.encode(initialPassword);

                // Build avatar color randomly or default
                String[] colors = {"#005B41", "#4F46E5", "#10B981", "#F59E0B", "#EF4444"};
                String avatarColor = colors[Math.abs(employeeId.hashCode()) % colors.length];

                User newUser = User.builder()
                        .username(email)
                        .email(email)
                        .password(encodedPassword)
                        .fullName(fullName)
                        .employeeId(employeeId)
                        .role("ROLE_USER")
                        .points(0)
                        .userGroup(userGroup)
                        .avatarColor(avatarColor)
                        .mustChangePassword(true)
                        .build();

                userRepository.save(newUser);
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi import dữ liệu Excel: " + e.getMessage(), e);
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // Check if double is a whole number
                double numericVal = cell.getNumericCellValue();
                if (numericVal == (long) numericVal) {
                    return String.format("%d", (long) numericVal);
                }
                return String.valueOf(numericVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    @Override
    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Template");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Create headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Mã nhân viên (*)", "User email (*)", "Tên nhân viên (*)", "Nhóm (Ví dụ: CIF, TIEN_VAY...)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create sample row
            Row sampleRow = sheet.createRow(1);
            sampleRow.createCell(0).setCellValue(1);
            sampleRow.createCell(1).setCellValue("NV0982");
            sampleRow.createCell(2).setCellValue("nguyenvanan@bidv.com.vn");
            sampleRow.createCell(3).setCellValue("Nguyễn Văn An");
            sampleRow.createCell(4).setCellValue("CIF");

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo file Excel mẫu: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UserGroup> getAllUserGroups() {
        return userGroupRepository.findAll();
    }
}
