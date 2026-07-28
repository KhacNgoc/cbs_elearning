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

    public AdminServiceImpl(TopicRepository topicRepository, LessonRepository lessonRepository,
                            QuestionRepository questionRepository, CourseMapper courseMapper,
                            QuizMapper quizMapper, UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.courseMapper = courseMapper;
        this.quizMapper = quizMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
                .videoUrl(lessonDto.getVideoUrl())
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
        lesson.setVideoUrl(lessonDto.getVideoUrl());
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
                    .fileUrl(documentDto.getFileUrl())
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
        question.setOptions(questionDto.getOptions() != null ? questionDto.getOptions() : new ArrayList<>());
        question.setCorrectAnswer(questionDto.getCorrectAnswer());

        Question saved = questionRepository.save(question);
        return quizMapper.toQuestionDto(saved);
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
