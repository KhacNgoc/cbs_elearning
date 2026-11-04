package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.dto.QuizGenerationResponseDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.CourseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class QuizGenerationServiceImpl implements QuizGenerationService {
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonKnowledgeSourceService knowledgeSources;
    private final RestTemplate restTemplate;
    private final String ragUrl;

    public QuizGenerationServiceImpl(
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            LessonKnowledgeSourceService knowledgeSources,
            @Value("${app.rag-url}") String ragUrl,
            @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.rag.quiz-read-timeout-ms:300000}") int readTimeoutMs) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.knowledgeSources = knowledgeSources;
        this.ragUrl = ragUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public QuizGenerationResponseDto generate(QuizGenerationRequestDto request) {
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
        try {
            QuizGenerationResponseDto result = restTemplate.postForObject(
                    ragUrl + "/quiz/generate", ragRequest, QuizGenerationResponseDto.class);
            if (result == null) {
                throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi trả về dữ liệu rỗng");
            }
            return result;
        } catch (HttpStatusCodeException exception) {
            log.error("RAG quiz generation failed courseId={} lessonId={} status={} response={}",
                    course.getId(), request.getLessonId(), exception.getStatusCode(),
                    exception.getResponseBodyAsString());
            throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi tạm thời không khả dụng", exception);
        } catch (RestClientException exception) {
            log.error("RAG quiz generation request failed courseId={} lessonId={} errorType={} message={}",
                    course.getId(), request.getLessonId(), exception.getClass().getSimpleName(),
                    exception.getMessage());
            throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi tạm thời không khả dụng", exception);
        }
    }
}
