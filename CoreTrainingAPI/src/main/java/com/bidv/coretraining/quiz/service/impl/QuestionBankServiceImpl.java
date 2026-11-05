package com.bidv.coretraining.quiz.service.impl;
import com.bidv.coretraining.policy.QuestionValidation;


import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.quiz.dto.BankQuestionDto;
import com.bidv.coretraining.quiz.dto.QuestionDto;
import com.bidv.coretraining.quiz.entity.BankQuestion;
import com.bidv.coretraining.quiz.entity.Question;
import com.bidv.coretraining.quiz.repository.BankQuestionRepository;
import com.bidv.coretraining.quiz.repository.QuestionRepository;
import com.bidv.coretraining.quiz.service.QuestionBankService;
import com.bidv.coretraining.quiz.service.QuizAdminService;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.training.entity.TrainingDomain;
import com.bidv.coretraining.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class QuestionBankServiceImpl implements QuestionBankService {
    private final BankQuestionRepository bank;
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final QuestionRepository questions;
    private final AuthorizationService auth;
    private final QuizAdminService admin;

    public QuestionBankServiceImpl(BankQuestionRepository bank, CourseRepository courses, LessonRepository lessons,
            QuestionRepository questions, AuthorizationService auth, QuizAdminService admin) {
        this.bank = bank;
        this.courses = courses;
        this.lessons = lessons;
        this.questions = questions;
        this.auth = auth;
        this.admin = admin;
    }

    private void require(Course c, String action) {
        AccessPolicy.require(AccessPolicy.allowsCourse(auth.current(), "exams:" + action, c));
    }

    private BankQuestion entry(Long id) {
        return bank.findById(id).orElseThrow(() -> new ResourceNotFoundException("BankQuestion"));
    }

    @Transactional(readOnly = true)
    public List<BankQuestionDto> list() {
        User actor = auth.current();
        return bank.findAll().stream().filter(q -> AccessPolicy.allowsCourse(actor, "exams:read", q.getCourse()))
                .sorted(Comparator.comparing(BankQuestion::getUpdatedAt).reversed()).map(this::dto).toList();
    }

    private String generateQuestionCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = "Q_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (!bank.existsByCodeIgnoreCaseAndIdNot(candidate, -1L)) {
                return candidate;
            }
        }
        return "Q_" + System.currentTimeMillis();
    }

    public BankQuestionDto save(Long id, BankQuestionDto request) {
        BankQuestion q = id == null ? new BankQuestion() : entry(id);
        if (id != null) {
            require(q.getCourse(), "update");
            if (!Objects.equals(q.getVersion(), request.version()))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Câu hỏi đã thay đổi. Hãy tải lại.");
        }
        Course course = courses.findById(request.courseId() == null ? "" : request.courseId())
                .orElseThrow(() -> new BadRequestException("Chọn khóa học quản lý câu hỏi"));
        require(course, id == null ? "create" : "update");
        String code;
        if (id == null) {
            code = (request.code() == null || request.code().isBlank())
                    ? generateQuestionCode()
                    : request.code().trim().toUpperCase(Locale.ROOT);
        } else {
            code = (request.code() == null || request.code().isBlank())
                    ? q.getCode()
                    : request.code().trim().toUpperCase(Locale.ROOT);
        }
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,79}"))
            throw new BadRequestException("Mã câu hỏi gồm chữ, số, dấu gạch ngang hoặc gạch dưới, tối đa 80 ký tự");
        if (bank.existsByCodeIgnoreCaseAndIdNot(code, id == null ? -1L : id))
            throw new BadRequestException("Mã câu hỏi đã tồn tại");
        Lesson related = null;
        if (request.lessonId() != null && !request.lessonId().isBlank()) {
            related = lessons.findById(request.lessonId())
                    .orElseThrow(() -> new BadRequestException("Bài học không tồn tại"));
            if (!related.getCourse().getId().equals(course.getId()))
                throw new BadRequestException("Bài học không thuộc khóa đã chọn");
        }
        QuestionDto input = QuestionDto.builder().text(request.text()).options(request.options())
                .correctAnswers(request.correctAnswers()).type(request.type()).explanation(request.explanation())
                .build();
        QuestionValidation.normalize(input);
        if (request.difficulty() == null || request.status() == null)
            throw new BadRequestException("Chọn mức độ khó và trạng thái");
        q.setCode(code);
        q.setCourse(course);
        q.setRelatedLesson(related);
        q.setText(input.getText());
        q.setType(input.getType());
        q.setOptions(new ArrayList<>(input.getOptions()));
        q.setCorrectAnswers(new ArrayList<>(input.getCorrectAnswers()));
        q.setExplanation(input.getExplanation());
        q.setDifficulty(request.difficulty());
        q.setStatus(request.status());
        q.setUpdatedAt(java.time.LocalDateTime.now());
        return dto(bank.saveAndFlush(q));
    }

    public BankQuestionDto capture(Long questionId) {
        Question q = questions.findById(questionId).orElseThrow(() -> new ResourceNotFoundException("Question"));
        require(q.getCourse(), "read");
        require(q.getCourse(), "create");
        if (q.getBankQuestionId() != null) {
            var existing = bank.findById(q.getBankQuestionId());
            if (existing.isPresent()) {
                require(existing.get().getCourse(), "read");
                return dto(existing.get());
            }
        }
        var saved = save(null,
                new BankQuestionDto(null, null, "Q-" + UUID.randomUUID(), q.getCourse().getId(), null,
                        q.getLesson() == null ? null : q.getLesson().getId(), null, null, null,
                        q.getText(), q.getType(), q.getOptions(),
                        q.getCorrectAnswers().isEmpty() ? List.of(q.getCorrectAnswer()) : q.getCorrectAnswers(),
                        q.getExplanation(), BankQuestion.Difficulty.MEDIUM, BankQuestion.Status.ACTIVE, null, null));
        q.setBankQuestionId(saved.id());
        q.setBankQuestionCode(saved.code());
        return saved;
    }

    public List<QuestionDto> addToQuiz(String lessonId, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size())
            throw new BadRequestException("Chọn từ 1 đến 100 câu hỏi khác nhau");
        Lesson target = lessons.findById(lessonId).orElseThrow(() -> new ResourceNotFoundException("Lesson"));
        require(target.getCourse(), "create");
        if (!"DRAFT".equals(target.getCourse().getStatus()))
            throw new BadRequestException("Chuyển khóa đích về nháp trước khi thêm câu hỏi");
        List<QuestionDto> result = new ArrayList<>();
        Set<Long> existing = questions.findByLessonId(lessonId).stream().map(Question::getBankQuestionId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for (Long id : ids) {
            if (existing.contains(id))
                throw new BadRequestException("Câu hỏi đã có trong bài kiểm tra");
            BankQuestion source = entry(id);
            require(source.getCourse(), "read");
            if (source.getStatus() != BankQuestion.Status.ACTIVE)
                throw new BadRequestException("Chỉ sử dụng câu hỏi đang hoạt động");
            QuestionDto saved = admin.createQuestion(QuestionDto.builder().courseId(target.getCourse().getId())
                    .lessonId(target.getId()).text(source.getText()).type(source.getType())
                    .options(new ArrayList<>(source.getOptions()))
                    .correctAnswers(new ArrayList<>(source.getCorrectAnswers())).explanation(source.getExplanation())
                    .build());
            Question copy = questions.findById(saved.getId()).orElseThrow();
            copy.setBankQuestionId(source.getId());
            copy.setBankQuestionCode(source.getCode());
            saved.setBankQuestionId(source.getId());
            saved.setBankQuestionCode(source.getCode());
            result.add(saved);
        }
        return result;
    }

    private BankQuestionDto dto(BankQuestion q) {
        Course c = q.getCourse();
        TrainingDomain d = c.getProgram() == null ? null : c.getProgram().getDomain();
        User actor = auth.current();
        return new BankQuestionDto(q.getId(), q.getVersion(), q.getCode(), c.getId(), c.getName(),
                q.getRelatedLesson() == null ? null : q.getRelatedLesson().getId(),
                q.getRelatedLesson() == null ? null : q.getRelatedLesson().getTitle(),
                d == null ? null : d.getId(), d == null ? null : d.getName(), q.getText(), q.getType(),
                List.copyOf(q.getOptions()), List.copyOf(q.getCorrectAnswers()), q.getExplanation(), q.getDifficulty(),
                q.getStatus(), q.getUpdatedAt(),
                java.util.stream.Stream.of("read", "create", "update")
                        .filter(a -> AccessPolicy.allowsCourse(actor, "exams:" + a, c)).toList());
    }
}
