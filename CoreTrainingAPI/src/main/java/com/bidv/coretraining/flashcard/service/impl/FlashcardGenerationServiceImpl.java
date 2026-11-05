package com.bidv.coretraining.flashcard.service.impl;

import com.bidv.coretraining.enums.FlashcardType;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.flashcard.dto.FlashcardDto;
import com.bidv.coretraining.flashcard.dto.FlashcardGenerationJobDto;
import com.bidv.coretraining.flashcard.service.FlashcardGenerationService;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.service.LessonKnowledgeSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FlashcardGenerationServiceImpl implements FlashcardGenerationService {
    private static final String UNAVAILABLE = "Dịch vụ tạo flashcard tạm thời không khả dụng";
    private static final String EMPTY = "Dịch vụ tạo flashcard trả về dữ liệu rỗng";
    private static final Pattern JOB_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    // Outlives the RAG service's own retention so an expired job surfaces as its 404.
    private static final Duration JOB_OWNER_RETENTION = Duration.ofHours(1);

    private final LessonKnowledgeSourceService knowledgeSources;
    private final RestTemplate restTemplate;
    private final String ragUrl;
    private final Map<String, JobOwner> jobOwners = new ConcurrentHashMap<>();

    private record JobOwner(String lessonId, Instant startedAt) {
    }

    public FlashcardGenerationServiceImpl(
            LessonKnowledgeSourceService knowledgeSources,
            @Value("${app.rag-url}") String ragUrl,
            @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.rag.read-timeout-ms:30000}") int readTimeoutMs) {
        this.knowledgeSources = knowledgeSources;
        this.ragUrl = ragUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public Target target(Lesson lesson) {
        // Same rule as lesson quizzes: drafting never widens past the saved sources.
        return new Target(lesson.getCourse().getId(), lesson.getCourse().getName(), lesson.getId(),
                lesson.getTitle(), knowledgeSources.generationSources(lesson));
    }

    @Override
    public FlashcardGenerationJobDto startJob(Target target, FlashcardDto.GenerationRequest request) {
        List<FlashcardType> types = request.types() == null || request.types().isEmpty()
                ? List.of(FlashcardType.TERM, FlashcardType.CLOZE)
                : request.types().stream().distinct().toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("course_id", target.courseId());
        body.put("course_name", target.courseName());
        body.put("lesson_id", target.lessonId());
        body.put("lesson_name", target.lessonName());
        body.put("sources", target.sources());
        body.put("count", request.count());
        body.put("types", types.stream().map(Enum::name).toList());

        FlashcardGenerationJobDto job;
        try {
            job = restTemplate.postForObject(ragUrl + "/flashcards/jobs", body, FlashcardGenerationJobDto.class);
        } catch (RestClientException exception) {
            if (exception instanceof HttpStatusCodeException status) {
                if (status.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    // The RAG service caps concurrent AI jobs for quizzes and flashcards together.
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Hệ thống AI đang bận. Vui lòng thử lại sau ít phút.");
                }
                log.error("RAG flashcard job start failed lessonId={} status={} response={}",
                        target.lessonId(), status.getStatusCode(), status.getResponseBodyAsString());
            } else {
                log.error("RAG flashcard job start request failed lessonId={} errorType={} message={}",
                        target.lessonId(), exception.getClass().getSimpleName(), exception.getMessage());
            }
            throw new RagServiceUnavailableException(UNAVAILABLE, exception);
        }
        if (job == null || job.getJobId() == null || !JOB_ID.matcher(job.getJobId()).matches()) {
            throw new RagServiceUnavailableException(EMPTY);
        }
        Instant now = Instant.now();
        jobOwners.values().removeIf(owner -> owner.startedAt().isBefore(now.minus(JOB_OWNER_RETENTION)));
        jobOwners.put(job.getJobId(), new JobOwner(target.lessonId(), now));
        return localize(job);
    }

    @Override
    public String jobLessonId(String jobId) {
        JobOwner owner = jobId == null ? null : jobOwners.get(jobId);
        if (owner == null) {
            throw new ResourceNotFoundException("Không tìm thấy tiến trình tạo flashcard");
        }
        return owner.lessonId();
    }

    @Override
    public FlashcardGenerationJobDto jobStatus(String jobId) {
        String lessonId = jobLessonId(jobId);
        FlashcardGenerationJobDto job;
        try {
            job = restTemplate.getForObject(ragUrl + "/flashcards/jobs/{jobId}", FlashcardGenerationJobDto.class,
                    jobId);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                jobOwners.remove(jobId);
                throw new ResourceNotFoundException("Tiến trình tạo flashcard đã hết hạn hoặc không còn tồn tại");
            }
            log.error("RAG flashcard job status failed jobId={} lessonId={} status={} response={}",
                    jobId, lessonId, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new RagServiceUnavailableException(UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            log.error("RAG flashcard job status request failed jobId={} lessonId={} errorType={} message={}",
                    jobId, lessonId, exception.getClass().getSimpleName(), exception.getMessage());
            throw new RagServiceUnavailableException(UNAVAILABLE, exception);
        }
        if (job == null) {
            throw new RagServiceUnavailableException(EMPTY);
        }
        if ("failed".equals(job.getStatus())) {
            log.error("RAG flashcard job failed jobId={} lessonId={} errorStatus={} error={}",
                    jobId, lessonId, job.getErrorStatus(), job.getError());
        }
        return localize(job);
    }

    private FlashcardGenerationJobDto localize(FlashcardGenerationJobDto job) {
        if ("failed".equals(job.getStatus())) {
            Integer status = job.getErrorStatus();
            if (status != null && status == HttpStatus.GATEWAY_TIMEOUT.value()) {
                job.setError("Quá thời gian tạo flashcard. Hãy giảm số thẻ hoặc thử lại.");
            } else if (status != null && status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                job.setError("Hệ thống AI đang bận. Vui lòng thử lại sau ít phút.");
            } else if (status != null && status >= 400 && status < 500) {
                job.setError("Yêu cầu tạo flashcard không hợp lệ");
            } else {
                job.setError(UNAVAILABLE);
            }
        }
        return job;
    }
}
