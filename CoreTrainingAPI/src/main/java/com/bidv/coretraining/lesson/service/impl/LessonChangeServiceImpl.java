package com.bidv.coretraining.lesson.service.impl;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.lesson.dto.LessonChangeDto;
import com.bidv.coretraining.lesson.dto.LessonChangeLogDto;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.entity.LessonChangeLog;
import com.bidv.coretraining.lesson.repository.LessonChangeLogRepository;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.service.LessonChangeService;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class LessonChangeServiceImpl implements LessonChangeService {
    private final LessonRepository lessonRepository;
    private final LessonChangeLogRepository changeLogRepository;
    private final NotificationService notificationService;
    private final AuthorizationService authorization;
    private final LearningAccessService learningAccess;
    private final RestTemplate restTemplate;
    private final String ragUrl;

    public LessonChangeServiceImpl(
            LessonRepository lessonRepository,
            LessonChangeLogRepository changeLogRepository,
            NotificationService notificationService,
            AuthorizationService authorization,
            LearningAccessService learningAccess,
            @Value("${app.rag-url:http://rag-service:8001}") String ragUrl,
            @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.rag.summary-timeout-ms:15000}") int summaryTimeoutMs) {
        this.lessonRepository = lessonRepository;
        this.changeLogRepository = changeLogRepository;
        this.notificationService = notificationService;
        this.authorization = authorization;
        this.learningAccess = learningAccess;
        this.ragUrl = ragUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(summaryTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public LessonChangeDto.CompletedLearnersCount countCompletedLearners(String lessonId) {
        return new LessonChangeDto.CompletedLearnersCount(notificationService.countCompletedLearners(lessonId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LessonChangeLogDto> getLatestChangeLog(String lessonId) {
        learningAccess.requireLesson(authorization.current(), lessonId);
        return changeLogRepository.findTopByLessonIdOrderByCreatedAtDesc(lessonId)
                .map(log -> new LessonChangeLogDto(
                        log.getId(),
                        log.getLesson().getId(),
                        log.getVersion(),
                        log.getChangeSummary(),
                        log.getUpdatedBy(),
                        log.getCreatedAt()));
    }

    @Override
    public LessonChangeDto.AiSummary summarizeChanges(String lessonId, LessonChangeDto.AiSummaryRequest request) {
        String title = lessonRepository.findById(lessonId).map(Lesson::getTitle).orElse("Bài học");
        String oldContent = (request != null && request.oldContent() != null) ? request.oldContent() : "";
        String newContent = (request != null && request.newContent() != null) ? request.newContent() : "";
        return new LessonChangeDto.AiSummary(generateAiSummary(title, oldContent, newContent));
    }

    private String generateAiSummary(String lessonTitle, String oldContent, String newContent) {
        try {
            Map<String, String> request = Map.of(
                    "lesson_title", lessonTitle != null ? lessonTitle : "",
                    "old_content", oldContent != null ? oldContent : "",
                    "new_content", newContent != null ? newContent : ""
            );
            AiSummaryResponse res = restTemplate.postForObject(
                    ragUrl + "/summarize-lesson-diff",
                    request,
                    AiSummaryResponse.class);
            if (res != null && res.summary() != null && !res.summary().isBlank()) {
                return res.summary().trim();
            }
        } catch (Exception ex) {
            log.warn("AI summary generation failed or timed out: {}", ex.getMessage());
        }
        return "Cập nhật nội dung bài giảng và tài liệu học tập mới.";
    }

    @Override
    @Transactional
    public LessonChangeDto.NotifyResult applyChangeAndNotify(String lessonId, LessonChangeDto.ChangeNotifyRequest request) {
        String updatedBy = authorization.current() != null ? authorization.current().getUsername() : "system";
        boolean notify = request.notifyLearners() != null && request.notifyLearners();
        return new LessonChangeDto.NotifyResult(
                applyLessonChangeAndNotify(lessonId, request.changeSummary(), updatedBy, notify));
    }

    private int applyLessonChangeAndNotify(String lessonId, String changeSummary, String updatedBy, boolean notifyLearners) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        String summary = (changeSummary != null && !changeSummary.isBlank())
                ? changeSummary.trim()
                : "Cập nhật nội dung kiến thức mới cho bài học.";

        // 1. Save change log entry
        LessonChangeLog logEntry = LessonChangeLog.builder()
                .lesson(lesson)
                .version(lesson.getVersion())
                .changeSummary(summary)
                .updatedBy(updatedBy)
                .createdAt(LocalDateTime.now())
                .build();
        changeLogRepository.save(logEntry);

        // 2. If notifyLearners is requested, send notification to completed learners only
        if (notifyLearners) {
            String courseId = lesson.getCourse() != null ? lesson.getCourse().getId() : "";
            String courseName = lesson.getCourse() != null ? lesson.getCourse().getName() : "khóa học";
            String title = "[Cập nhật kiến thức] Bài học \"" + lesson.getTitle() + "\" vừa có nội dung mới";
            String targetUrl = "/learning/" + courseId + "/lessons/" + lesson.getId();

            int notifiedCount = notificationService.notifyLessonUpdateToCompletedLearners(
                    lessonId, title, summary, targetUrl);
            log.info("Notified {} completed learners for lesson update {}", notifiedCount, lessonId);
            return notifiedCount;
        }

        return 0;
    }

    private record AiSummaryResponse(String summary) {}
}
