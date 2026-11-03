package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.policy.QuestionValidation;
import com.bidv.coretraining.service.QuizGenerationService;
import com.bidv.coretraining.service.TrainingStructureService;
import com.bidv.coretraining.service.AuthorizationService;
import com.bidv.coretraining.mapper.QuizMapper;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import com.bidv.coretraining.service.QuizAdminService;

@Service
public class QuizAdminServiceImpl implements QuizAdminService {
    private final AuthorizationService authorization;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final QuizMapper quizMapper;
    private final TrainingStructureService structure;
    private final QuizGenerationService quizGeneration;

    public QuizAdminServiceImpl(AuthorizationService authorization, CourseRepository courseRepository,
            LessonRepository lessonRepository, QuestionRepository questionRepository, QuizMapper quizMapper,
            TrainingStructureService structure, QuizGenerationService quizGeneration) {
        this.authorization = authorization;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.quizMapper = quizMapper;
        this.structure = structure;
        this.quizGeneration = quizGeneration;
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

        Lesson quizLesson = resolveQuestionLesson(questionDto, course);
        if (quizLesson == null)
            throw new BadRequestException("Bài kiểm tra phải thuộc một bài học");
        if ("DELETED".equals(course.getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        quizLesson.ensureQuiz();
        lessonRepository.saveAndFlush(quizLesson);
        Question question = Question.builder()
                .quiz(quizLesson.getQuiz())
                .course(course)
                .lesson(resolveQuestionLesson(questionDto, course))
                .text(questionDto.getText())
                .explanation(questionDto.getExplanation())
                .options(new ArrayList<>(questionDto.getOptions()))
                .correctAnswer(questionDto.getCorrectAnswer())
                .correctAnswers(new ArrayList<>(questionDto.getCorrectAnswers()))
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

        if ("DELETED".equals(question.getCourse().getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        if (!java.util.Objects.equals(question.getLesson().getId(), questionDto.getLessonId()))
            throw new BadRequestException("Không đổi phạm vi câu hỏi");
        question.setText(questionDto.getText());
        question.setExplanation(questionDto.getExplanation());
        question.setLesson(resolveQuestionLesson(questionDto, question.getCourse()));
        // Keep Hibernate's managed collections; normalized DTO lists are immutable.
        question.getOptions().clear();
        question.getOptions().addAll(questionDto.getOptions());
        question.setCorrectAnswer(questionDto.getCorrectAnswer());
        question.getCorrectAnswers().clear();
        question.getCorrectAnswers().addAll(questionDto.getCorrectAnswers());
        question.setType(questionDto.getType());

        Question saved = questionRepository.save(question);
        return quizMapper.toQuestionDto(saved);
    }

    private void normalizeAndValidateQuestion(QuestionDto questionDto) {
        QuestionValidation.normalize(questionDto);
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
        if ("DELETED".equals(question.getCourse().getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        questionRepository.delete(question);
    }


    @Override
    @Transactional
    public QuizSettingsDto updateQuizSettings(QuizSettingsDto settings) {
        authorization.edit(settings.getCourseId());
        int passingScore = settings.getPassingScore() == null ? 80 : settings.getPassingScore();
        if (passingScore < 1 || passingScore > 100)
            throw new BadRequestException("Ngưỡng đạt phải từ 1 đến 100");
        Integer timeLimit = settings.getTimeLimitMinutes();
        if (timeLimit != null && (timeLimit < 1 || timeLimit > 300))
            throw new BadRequestException("Thời gian làm bài phải từ 1 đến 300 phút");
        if (settings.getLessonId() == null)
            throw new BadRequestException("Kiểm tra phải gắn với bài học");
        Lesson lesson = structure.lesson(settings.getLessonId());
        if (!lesson.getCourse().getId().equals(settings.getCourseId()))
            throw new BadRequestException("Bài không thuộc khóa");
        authorization.edit(lesson.getCourse());
        if ("DELETED".equals(lesson.getCourse().getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        if (!lesson.hasQuiz())
            throw new BadRequestException("Thêm ít nhất một câu hỏi trước khi lưu cấu hình bài kiểm tra");
        lesson.getQuiz().setPassingScore(passingScore);
        lesson.getQuiz().setRequiredForProgress(!Boolean.FALSE.equals(settings.getRequiredForProgress()));
        lesson.getQuiz().setTimeLimitMinutes(timeLimit);
        lessonRepository.save(lesson);
        settings.setPassingScore(passingScore);
        return settings;
    }

    @Override
    @Transactional
    public void deleteQuiz(String courseId, String lessonId) {
        authorization.edit(courseId);
        if (lessonId == null)
            throw new BadRequestException("Kiểm tra phải gắn với bài học");
        Lesson lesson = structure.lesson(lessonId);
        if (!lesson.getCourse().getId().equals(courseId))
            throw new BadRequestException("Bài không thuộc khóa");
        authorization.edit(lesson.getCourse());
        if ("DELETED".equals(lesson.getCourse().getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
        questionRepository.deleteByLessonId(lessonId);
        questionRepository.flush();
        lesson.setQuiz(null);
        lessonRepository.save(lesson);
    }

    // User CRUD implementation

    @Override
    public QuizGenerationResponseDto generateQuiz(QuizGenerationRequestDto request) {
        authorization.edit(request.getCourseId());
        return quizGeneration.generate(request);
    }

    @Override
    public QuizGenerationJobDto startQuizGenerationJob(QuizGenerationRequestDto request) {
        authorization.edit(request.getCourseId());
        return quizGeneration.startJob(request);
    }

    @Override
    public QuizGenerationJobDto getQuizGenerationJob(String jobId) {
        authorization.edit(quizGeneration.jobCourseId(jobId));
        return quizGeneration.jobStatus(jobId);
    }
}
