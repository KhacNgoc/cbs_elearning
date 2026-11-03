package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.QuizGenerationJobDto;
import com.bidv.coretraining.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.dto.QuizGenerationResponseDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.BankQuestionRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.QuestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class QuizGenerationServiceImpl implements QuizGenerationService {
    private static final String UNAVAILABLE = "Dịch vụ tạo câu hỏi tạm thời không khả dụng";
    private static final Pattern JOB_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    // Outlives the RAG service's own retention so an expired job surfaces as its 404.
    private static final Duration JOB_OWNER_RETENTION = Duration.ofHours(1);
    // Bounds the payload and the RAG service's duplicate checks; recent questions come first.
    private static final int MAX_EXISTING_QUESTIONS = 300;
    private static final int MAX_BANK_QUESTIONS = 200;
    private static final int MAX_QUESTION_LENGTH = 2000;

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonKnowledgeSourceService knowledgeSources;
    private final QuestionRepository questionRepository;
    private final BankQuestionRepository bankQuestionRepository;
    private final RestTemplate restTemplate;
    private final String ragUrl;
    private final Map<String, JobOwner> jobOwners = new ConcurrentHashMap<>();

    private record JobOwner(String courseId, Instant startedAt) {
    }

    private record RagQuizRequest(String courseId, String lessonId, Map<String, Object> body) {
    }

    public QuizGenerationServiceImpl(
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            LessonKnowledgeSourceService knowledgeSources,
            QuestionRepository questionRepository,
            BankQuestionRepository bankQuestionRepository,
            @Value("${app.rag-url}") String ragUrl,
            @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.rag.quiz-read-timeout-ms:300000}") int readTimeoutMs) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.knowledgeSources = knowledgeSources;
        this.questionRepository = questionRepository;
        this.bankQuestionRepository = bankQuestionRepository;
        this.ragUrl = ragUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public QuizGenerationResponseDto generate(QuizGenerationRequestDto request) {
        RagQuizRequest ragRequest = ragRequest(request);
        try {
            QuizGenerationResponseDto result = restTemplate.postForObject(
                    ragUrl + "/quiz/generate", ragRequest.body(), QuizGenerationResponseDto.class);
            if (result == null) {
                throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi trả về dữ liệu rỗng");
            }
            return result;
        } catch (RestClientException exception) {
            throw unavailable("generation", ragRequest, exception);
        }
    }

    @Override
    public QuizGenerationJobDto startJob(QuizGenerationRequestDto request) {
        RagQuizRequest ragRequest = ragRequest(request);
        QuizGenerationJobDto job;
        try {
            job = restTemplate.postForObject(ragUrl + "/quiz/jobs", ragRequest.body(), QuizGenerationJobDto.class);
        } catch (RestClientException exception) {
            throw unavailable("job start", ragRequest, exception);
        }
        if (job == null || job.getJobId() == null || !JOB_ID.matcher(job.getJobId()).matches()) {
            throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi trả về dữ liệu rỗng");
        }
        Instant now = Instant.now();
        jobOwners.values().removeIf(owner -> owner.startedAt().isBefore(now.minus(JOB_OWNER_RETENTION)));
        jobOwners.put(job.getJobId(), new JobOwner(ragRequest.courseId(), now));
        return localize(job);
    }

    @Override
    public String jobCourseId(String jobId) {
        JobOwner owner = jobId == null ? null : jobOwners.get(jobId);
        if (owner == null) {
            throw new ResourceNotFoundException("Không tìm thấy tiến trình tạo câu hỏi");
        }
        return owner.courseId();
    }

    @Override
    public QuizGenerationJobDto jobStatus(String jobId) {
        String courseId = jobCourseId(jobId);
        QuizGenerationJobDto job;
        try {
            job = restTemplate.getForObject(ragUrl + "/quiz/jobs/{jobId}", QuizGenerationJobDto.class, jobId);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                jobOwners.remove(jobId);
                throw new ResourceNotFoundException("Tiến trình tạo câu hỏi đã hết hạn hoặc không còn tồn tại");
            }
            log.error("RAG quiz job status failed jobId={} courseId={} status={} response={}",
                    jobId, courseId, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new RagServiceUnavailableException(UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            log.error("RAG quiz job status request failed jobId={} courseId={} errorType={} message={}",
                    jobId, courseId, exception.getClass().getSimpleName(), exception.getMessage());
            throw new RagServiceUnavailableException(UNAVAILABLE, exception);
        }
        if (job == null) {
            throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi trả về dữ liệu rỗng");
        }
        if ("failed".equals(job.getStatus())) {
            log.error("RAG quiz job failed jobId={} courseId={} errorStatus={} error={}",
                    jobId, courseId, job.getErrorStatus(), job.getError());
        }
        return localize(job);
    }

    private QuizGenerationJobDto localize(QuizGenerationJobDto job) {
        if ("failed".equals(job.getStatus())) {
            Integer status = job.getErrorStatus();
            if (status != null && status == HttpStatus.GATEWAY_TIMEOUT.value()) {
                job.setError("Quá thời gian tạo câu hỏi. Hãy giảm số câu hoặc thử lại.");
            } else if (status != null && status >= 400 && status < 500) {
                job.setError("Yêu cầu tạo câu hỏi không hợp lệ");
            } else {
                job.setError(UNAVAILABLE);
            }
        }
        return job;
    }

    private RagServiceUnavailableException unavailable(String operation, RagQuizRequest request,
            RestClientException exception) {
        if (exception instanceof HttpStatusCodeException statusException) {
            log.error("RAG quiz {} failed courseId={} lessonId={} status={} response={}",
                    operation, request.courseId(), request.lessonId(), statusException.getStatusCode(),
                    statusException.getResponseBodyAsString());
        } else {
            log.error("RAG quiz {} request failed courseId={} lessonId={} errorType={} message={}",
                    operation, request.courseId(), request.lessonId(), exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
        return new RagServiceUnavailableException(UNAVAILABLE, exception);
    }

    private RagQuizRequest ragRequest(QuizGenerationRequestDto request) {
        int total = request.getSingleChoiceCount() + request.getMultipleChoiceCount();
        if (total < 1 || total > 20) {
            throw new BadRequestException("Tổng số câu hỏi phải nằm trong khoảng từ 1 đến 20");
        }
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        String lessonName = null;
        java.util.List<Map<String, Object>> selectedSources = null;
        if (request.getLessonId() != null && !request.getLessonId().isBlank()) {
            Lesson lesson = lessonRepository.findById(request.getLessonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", request.getLessonId()));
            if (!lesson.getCourse().getId().equals(course.getId())) {
                throw new BadRequestException("Bài học không thuộc khóa học đã chọn");
            }
            // Deliberately send only the lesson title. Hint and procedure steps are not
            // retrieval context.
            lessonName = lesson.getTitle();
            selectedSources = knowledgeSources.generationSources(lesson);
        }

        Map<String, Object> ragRequest = new LinkedHashMap<>();
        ragRequest.put("course_id", course.getId());
        ragRequest.put("course_name", course.getName());
        if (lessonName != null)
            ragRequest.put("lesson_name", lessonName);
        if (selectedSources != null) {
            ragRequest.put("lesson_id", request.getLessonId());
            ragRequest.put("sources", selectedSources);
        }
        ragRequest.put("single_choice_count", request.getSingleChoiceCount());
        ragRequest.put("multiple_choice_count", request.getMultipleChoiceCount());
        ragRequest.put("option_count", request.getOptionCount());
        ragRequest.put("existing_questions", existingQuestions(course.getId(),
                selectedSources != null ? request.getLessonId() : null, request.getExcludeQuestions()));
        return new RagQuizRequest(course.getId(), request.getLessonId(), ragRequest);
    }

    /**
     * Questions a new draft must not repeat: the admin's unsaved drafts, the quiz's saved
     * questions and recent question-bank entries for the same lesson (or course).
     */
    private List<String> existingQuestions(String courseId, String lessonId, List<String> drafts) {
        var page = PageRequest.of(0, MAX_BANK_QUESTIONS);
        List<String> saved = lessonId != null
                ? questionRepository.findTextsByLessonId(lessonId)
                : questionRepository.findCourseQuizTexts(courseId);
        List<String> bank = lessonId != null
                ? bankQuestionRepository.findRecentTextsByLessonId(lessonId, page)
                : bankQuestionRepository.findRecentTextsByCourseId(courseId, page);
        Set<String> texts = new LinkedHashSet<>();
        for (List<String> group : List.of(drafts == null ? List.<String>of() : drafts, saved, bank)) {
            for (String text : group) {
                if (texts.size() >= MAX_EXISTING_QUESTIONS) {
                    return new ArrayList<>(texts);
                }
                if (text != null && !text.isBlank()) {
                    String trimmed = text.strip();
                    texts.add(trimmed.length() > MAX_QUESTION_LENGTH ? trimmed.substring(0, MAX_QUESTION_LENGTH) : trimmed);
                }
            }
        }
        return new ArrayList<>(texts);
    }
}
