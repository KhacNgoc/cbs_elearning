package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.RagChatHistoryDto;
import com.bidv.coretraining.dto.RagChatRequest;
import com.bidv.coretraining.dto.RagChatResponse;
import com.bidv.coretraining.dto.RagSourceDto;
import com.bidv.coretraining.entity.RagChatMessage;
import com.bidv.coretraining.entity.RagChatSource;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.RagChatMessageRepository;
import com.bidv.coretraining.repository.TopicRepository;
import com.bidv.coretraining.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagChatService {
    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final RagChatMessageRepository messageRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String ragUrl;

    public RagChatService(UserRepository userRepository,
                          TopicRepository topicRepository,
                          RagChatMessageRepository messageRepository,
                          @Value("${app.rag-url}") String ragUrl) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.messageRepository = messageRepository;
        this.ragUrl = ragUrl;
    }

    public RagChatResponse chat(String username, RagChatRequest request) {
        User user = requireUser(username);
        Topic topic = requireAccessibleTopic(user, request.courseId());

        Map<String, Object> pythonRequest = new LinkedHashMap<>();
        pythonRequest.put("question", request.question().trim());
        pythonRequest.put("course_id", topic.getId());
        if (request.page() != null) pythonRequest.put("page", request.page());

        RagChatResponse response = restTemplate.postForObject(
                ragUrl + "/chat", pythonRequest, RagChatResponse.class);
        if (response == null || response.answer() == null) {
            throw new IllegalStateException("Dịch vụ trợ lý trả về dữ liệu không hợp lệ");
        }
        RagChatResponse normalized = new RagChatResponse(
                response.answer(), response.grounded(),
                response.sources() == null ? List.of() : response.sources());
        saveHistory(user, topic, request.question().trim(), normalized);
        return normalized;
    }

    @Transactional(readOnly = true)
    public List<RagChatHistoryDto> history(String username, String courseId) {
        User user = requireUser(username);
        Topic topic = requireAccessibleTopic(user, courseId);
        List<RagChatMessage> messages = new ArrayList<>(
                messageRepository.findTop50ByUserIdAndTopicIdOrderByCreatedAtDesc(user.getId(), topic.getId()));
        Collections.reverse(messages);
        return messages.stream().map(this::toHistoryDto).toList();
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private Topic requireAccessibleTopic(User user, String topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));
        boolean accessible = "ROLE_ADMIN".equals(user.getRole())
                || userRepository.hasRegisteredTopic(user.getUsername(), topicId)
                || userRepository.hasMandatoryTopic(user.getUsername(), topicId);
        if (!accessible) {
            throw new AccessDeniedException("Bạn không có quyền truy cập tài liệu của khóa học này");
        }
        return topic;
    }

    private void saveHistory(User user, Topic topic, String question, RagChatResponse response) {
        RagChatMessage message = RagChatMessage.builder()
                .user(user)
                .topic(topic)
                .question(question)
                .answer(response.answer())
                .grounded(response.grounded())
                .createdAt(LocalDateTime.now())
                .build();
        for (RagSourceDto source : response.sources()) {
            message.addSource(RagChatSource.builder()
                    .documentId(source.documentId())
                    .documentName(source.documentName())
                    .page(source.page())
                    .section(source.section())
                    .build());
        }
        messageRepository.save(message);
    }

    private RagChatHistoryDto toHistoryDto(RagChatMessage message) {
        return new RagChatHistoryDto(
                message.getId(),
                message.getTopic().getId(),
                message.getQuestion(),
                message.getAnswer(),
                message.isGrounded(),
                message.getSources().stream().map(source -> new RagSourceDto(
                        source.getDocumentId(), source.getDocumentName(),
                        source.getPage(), source.getSection())).toList(),
                message.getCreatedAt());
    }
}
