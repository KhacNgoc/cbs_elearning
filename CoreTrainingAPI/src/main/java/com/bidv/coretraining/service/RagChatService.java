package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class RagChatService {
    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final RagConversationRepository conversationRepository;
    private final RagConversationMessageRepository messageRepository;
    private final RagChatMessageRepository legacyMessageRepository;
    private final RestTemplate restTemplate;
    private final String ragUrl;
    private final int recentMessageLimit;
    private final int recentCharacterLimit;
    private final int summaryMessageThreshold;
    private final int summaryCharacterThreshold;

    public RagChatService(UserRepository userRepository,
                          TopicRepository topicRepository,
                          RagConversationRepository conversationRepository,
                          RagConversationMessageRepository messageRepository,
                          RagChatMessageRepository legacyMessageRepository,
                          @Value("${app.rag-url}") String ragUrl,
                          @Value("${app.rag.recent-message-limit:8}") int recentMessageLimit,
                          @Value("${app.rag.recent-character-limit:12000}") int recentCharacterLimit,
                          @Value("${app.rag.summary-message-threshold:12}") int summaryMessageThreshold,
                          @Value("${app.rag.summary-character-threshold:24000}") int summaryCharacterThreshold,
                          @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
                          @Value("${app.rag.read-timeout-ms:300000}") int readTimeoutMs) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.legacyMessageRepository = legacyMessageRepository;
        this.ragUrl = ragUrl;
        int cappedRecentLimit = Math.max(2, Math.min(recentMessageLimit, 30));
        this.recentMessageLimit = cappedRecentLimit % 2 == 0 ? cappedRecentLimit : cappedRecentLimit - 1;
        this.recentCharacterLimit = Math.max(1000, recentCharacterLimit);
        this.summaryMessageThreshold = Math.max(3, summaryMessageThreshold);
        this.summaryCharacterThreshold = Math.max(2000, summaryCharacterThreshold);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public RagChatResponse chat(String username, RagChatRequest request) {
        long startedAt = System.nanoTime();
        User user = requireUser(username);
        Topic topic = requireAccessibleTopic(user, request.courseId());
        RagConversation conversation = resolveConversation(user, topic, request.conversationId());

        String question = request.question().trim();
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(question.substring(0, Math.min(question.length(), 120)));
            touch(conversation);
        }
        RagConversationMessage userMessage = messageRepository.save(RagConversationMessage.builder()
                .conversation(conversation)
                .role("user")
                .content(question)
                .originalQuestion(question)
                .createdAt(LocalDateTime.now())
                .build());
        touch(conversation);

        List<RagConversationMessage> recent = recentBefore(conversation.getId(), userMessage.getId());
        Map<String, Object> pythonRequest = new LinkedHashMap<>();
        pythonRequest.put("question", question);
        pythonRequest.put("course_id", topic.getId());
        if (request.page() != null) pythonRequest.put("page", request.page());
        pythonRequest.put("conversation_summary", Objects.toString(conversation.getSummary(), ""));
        pythonRequest.put("recent_messages", recent.stream()
                .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
                .toList());

        RagServiceChatResponse ragResponse;
        try {
            ragResponse = restTemplate.postForObject(
                    ragUrl + "/chat", pythonRequest, RagServiceChatResponse.class);
        } catch (RestClientException exception) {
            log.error("RAG chat unavailable conversationId={} userId={} courseId={} errorType={}",
                    conversation.getId(), user.getId(), topic.getId(), exception.getClass().getSimpleName());
            throw new RagServiceUnavailableException("Trợ lý học tập tạm thời không khả dụng", exception);
        }
        if (ragResponse == null || ragResponse.answer() == null) {
            throw new RagServiceUnavailableException("Dịch vụ trợ lý trả về dữ liệu không hợp lệ");
        }

        List<RagSourceDto> sources = ragResponse.sources() == null ? List.of() : ragResponse.sources();
        RagConversationMessage assistantMessage = RagConversationMessage.builder()
                .conversation(conversation)
                .role("assistant")
                .content(ragResponse.answer())
                .grounded(ragResponse.grounded())
                .originalQuestion(question)
                .standaloneQuestion(ragResponse.standaloneQuestion())
                .needsClarification(ragResponse.needsClarification())
                .createdAt(LocalDateTime.now())
                .build();
        for (RagSourceDto source : sources) {
            assistantMessage.addSource(RagConversationSource.builder()
                    .documentId(source.documentId())
                    .documentName(source.documentName())
                    .page(source.page())
                    .section(source.section())
                    .build());
        }
        messageRepository.save(assistantMessage);
        touch(conversation);
        updateSummaryBestEffort(conversation, user.getId(), topic.getId());

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("RAG chat completed conversationId={} userId={} courseId={} grounded={} clarification={} sources={} elapsedMs={}",
                conversation.getId(), user.getId(), topic.getId(), ragResponse.grounded(),
                ragResponse.needsClarification(), sources.size(), elapsedMs);
        return new RagChatResponse(ragResponse.answer(), ragResponse.grounded(), sources,
                conversation.getId(), ragResponse.needsClarification());
    }

    @Transactional(readOnly = true)
    public List<RagChatHistoryDto> history(String username, String courseId, String conversationId) {
        User user = requireUser(username);
        Topic topic = requireAccessibleTopic(user, courseId);
        Optional<RagConversation> selected = conversationId == null || conversationId.isBlank()
                ? conversationRepository.findFirstByUserIdAndTopicIdOrderByUpdatedAtDesc(user.getId(), topic.getId())
                : Optional.of(requireOwnedConversation(user, topic, conversationId));
        if (selected.isEmpty()) return legacyHistory(user.getId(), topic.getId());

        RagConversation conversation = selected.get();
        List<RagConversationMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId());
        List<RagChatHistoryDto> result = new ArrayList<>();
        RagConversationMessage pendingUser = null;
        for (RagConversationMessage message : messages) {
            if ("user".equals(message.getRole())) {
                pendingUser = message;
            } else if ("assistant".equals(message.getRole()) && pendingUser != null) {
                result.add(new RagChatHistoryDto(
                        message.getId(), conversation.getId(), topic.getId(), pendingUser.getContent(),
                        message.getContent(), Boolean.TRUE.equals(message.getGrounded()),
                        Boolean.TRUE.equals(message.getNeedsClarification()),
                        message.getSources().stream().map(source -> new RagSourceDto(
                                source.getDocumentId(), source.getDocumentName(), source.getPage(), source.getSection())).toList(),
                        message.getCreatedAt()));
                pendingUser = null;
            }
        }
        return result.size() <= 50 ? result : result.subList(result.size() - 50, result.size());
    }

    private List<RagChatHistoryDto> legacyHistory(Long userId, String topicId) {
        List<RagChatMessage> messages = new ArrayList<>(
                legacyMessageRepository.findTop50ByUserIdAndTopicIdOrderByCreatedAtDesc(userId, topicId));
        Collections.reverse(messages);
        return messages.stream().map(message -> new RagChatHistoryDto(
                message.getId(), null, topicId, message.getQuestion(), message.getAnswer(),
                message.isGrounded(), false,
                message.getSources().stream().map(source -> new RagSourceDto(
                        source.getDocumentId(), source.getDocumentName(), source.getPage(), source.getSection())).toList(),
                message.getCreatedAt())).toList();
    }

    private RagConversation resolveConversation(User user, Topic topic, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return requireOwnedConversation(user, topic, conversationId);
        }
        LocalDateTime now = LocalDateTime.now();
        return conversationRepository.save(RagConversation.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .topic(topic)
                .summary("")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private RagConversation requireOwnedConversation(User user, Topic topic, String conversationId) {
        RagConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Cuộc hội thoại không thuộc người dùng hiện tại");
        }
        if (!conversation.getTopic().getId().equals(topic.getId())) {
            throw new BadRequestException("Cuộc hội thoại thuộc một khóa học khác");
        }
        return conversation;
    }

    private List<RagConversationMessage> recentBefore(String conversationId, Long currentMessageId) {
        List<RagConversationMessage> descending = messageRepository
                .findTop30ByConversationIdAndIdLessThanOrderByIdDesc(conversationId, currentMessageId);
        List<RagConversationMessage> selected = new ArrayList<>();
        int characters = 0;
        for (RagConversationMessage message : descending) {
            if (selected.size() >= recentMessageLimit || characters + message.getContent().length() > recentCharacterLimit) break;
            selected.add(message);
            characters += message.getContent().length();
        }
        Collections.reverse(selected);
        // Avoid starting the window with an orphan assistant response.
        if (!selected.isEmpty() && "assistant".equals(selected.get(0).getRole())) selected.remove(0);
        return selected;
    }

    private void updateSummaryBestEffort(RagConversation conversation, Long userId, String topicId) {
        try {
            long marker = conversation.getLastSummarizedMessageId() == null ? 0 : conversation.getLastSummarizedMessageId();
            List<RagConversationMessage> pending = messageRepository
                    .findByConversationIdAndIdGreaterThanOrderByIdAsc(conversation.getId(), marker);
            int pendingCharacters = pending.stream().mapToInt(message -> message.getContent().length()).sum();
            if (pending.size() < summaryMessageThreshold && pendingCharacters < summaryCharacterThreshold) return;

            int summarizeCount = Math.max(0, pending.size() - recentMessageLimit);
            if (summarizeCount == 0) return;
            List<RagConversationMessage> messagesToSummarize = pending.subList(0, summarizeCount);
            Map<String, Object> summaryRequest = Map.of(
                    "current_summary", Objects.toString(conversation.getSummary(), ""),
                    "new_messages", messagesToSummarize.stream()
                            .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
                            .toList());
            SummaryResponse summaryResponse = restTemplate.postForObject(
                    ragUrl + "/summarize", summaryRequest, SummaryResponse.class);
            if (summaryResponse == null || summaryResponse.summary() == null || summaryResponse.summary().isBlank()) {
                throw new IllegalStateException("Summary response is empty");
            }
            conversation.setSummary(summaryResponse.summary().trim());
            conversation.setLastSummarizedMessageId(messagesToSummarize.get(messagesToSummarize.size() - 1).getId());
            touch(conversation);
            log.info("Conversation summary updated conversationId={} userId={} courseId={} summarizedMessages={}",
                    conversation.getId(), userId, topicId, messagesToSummarize.size());
        } catch (RuntimeException exception) {
            log.warn("Conversation summary update failed conversationId={} userId={} courseId={} errorType={}",
                    conversation.getId(), userId, topicId, exception.getClass().getSimpleName());
        }
    }

    private void touch(RagConversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
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
        if (!accessible) throw new AccessDeniedException("Bạn không có quyền truy cập tài liệu của khóa học này");
        return topic;
    }

    private record SummaryResponse(String summary) {}
}
