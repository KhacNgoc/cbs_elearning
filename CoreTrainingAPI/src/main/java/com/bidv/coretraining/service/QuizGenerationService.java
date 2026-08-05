package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.dto.QuizGenerationResponseDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.TopicRepository;
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
public class QuizGenerationService {
    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final RestTemplate restTemplate;
    private final String ragUrl;

    public QuizGenerationService(
            TopicRepository topicRepository,
            LessonRepository lessonRepository,
            @Value("${app.rag-url}") String ragUrl,
            @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.rag.read-timeout-ms:300000}") int readTimeoutMs) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.ragUrl = ragUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public QuizGenerationResponseDto generate(QuizGenerationRequestDto request) {
        int total = request.getSingleChoiceCount() + request.getMultipleChoiceCount();
        if (total < 1 || total > 10) {
            throw new BadRequestException("Tổng số câu hỏi phải nằm trong khoảng từ 1 đến 10");
        }
        Topic topic = topicRepository.findById(request.getTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", request.getTopicId()));

        String lessonName = null;
        if (request.getLessonId() != null && !request.getLessonId().isBlank()) {
            Lesson lesson = lessonRepository.findById(request.getLessonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", request.getLessonId()));
            if (!lesson.getTopic().getId().equals(topic.getId())) {
                throw new BadRequestException("Bài học không thuộc chủ đề đã chọn");
            }
            // Deliberately send only the lesson title. Hint and procedure steps are not retrieval context.
            lessonName = lesson.getTitle();
        }

        Map<String, Object> ragRequest = new LinkedHashMap<>();
        ragRequest.put("course_id", topic.getId());
        ragRequest.put("topic_name", topic.getName());
        if (lessonName != null) ragRequest.put("lesson_name", lessonName);
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
            log.error("RAG quiz generation failed topicId={} lessonId={} status={} response={}",
                    topic.getId(), request.getLessonId(), exception.getStatusCode(),
                    exception.getResponseBodyAsString());
            throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi tạm thời không khả dụng", exception);
        } catch (RestClientException exception) {
            log.error("RAG quiz generation request failed topicId={} lessonId={} errorType={} message={}",
                    topic.getId(), request.getLessonId(), exception.getClass().getSimpleName(),
                    exception.getMessage());
            throw new RagServiceUnavailableException("Dịch vụ tạo câu hỏi tạm thời không khả dụng", exception);
        }
    }
}
