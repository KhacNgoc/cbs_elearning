package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.entity.RagConversation;
import com.bidv.coretraining.entity.RagConversationMessage;
import com.bidv.coretraining.repository.RagConversationMessageRepository;
import com.bidv.coretraining.repository.RagConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class RagConversationSummaryServiceImpl implements RagConversationSummaryService {
        private final RagConversationRepository conversationRepository;
        private final RagConversationMessageRepository messageRepository;
        private final RestTemplate restTemplate;
        private final String ragUrl;
        private final int recentMessageLimit;
        private final int summaryMessageThreshold;
        private final int summaryCharacterThreshold;

        public RagConversationSummaryServiceImpl(
                        RagConversationRepository conversationRepository,
                        RagConversationMessageRepository messageRepository,
                        @Value("${app.rag-url}") String ragUrl,
                        @Value("${app.rag.recent-message-limit:8}") int recentMessageLimit,
                        @Value("${app.rag.summary-message-threshold:12}") int summaryMessageThreshold,
                        @Value("${app.rag.summary-character-threshold:24000}") int summaryCharacterThreshold,
                        @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
                        @Value("${app.rag.summary-timeout-ms:10000}") int summaryTimeoutMs) {
                this.conversationRepository = conversationRepository;
                this.messageRepository = messageRepository;
                this.ragUrl = ragUrl;
                int cappedRecentLimit = Math.max(2, Math.min(recentMessageLimit, 30));
                this.recentMessageLimit = cappedRecentLimit % 2 == 0 ? cappedRecentLimit : cappedRecentLimit - 1;
                this.summaryMessageThreshold = Math.max(3, summaryMessageThreshold);
                this.summaryCharacterThreshold = Math.max(2000, summaryCharacterThreshold);
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(connectTimeoutMs);
                requestFactory.setReadTimeout(summaryTimeoutMs);
                this.restTemplate = new RestTemplate(requestFactory);
        }

        @Async("ragSummaryExecutor")
        @Transactional
        public void updateAsync(String conversationId, Long userId, String courseId) {
                try {
                        RagConversation conversation = conversationRepository.findById(conversationId).orElse(null);
                        if (conversation == null)
                                return;

                        long marker = conversation.getLastSummarizedMessageId() == null
                                        ? 0
                                        : conversation.getLastSummarizedMessageId();
                        List<RagConversationMessage> pending = messageRepository
                                        .findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId, marker);
                        int pendingCharacters = pending.stream().mapToInt(message -> message.getContent().length())
                                        .sum();
                        if (pending.size() < summaryMessageThreshold && pendingCharacters < summaryCharacterThreshold)
                                return;

                        int summarizeCount = Math.max(0, pending.size() - recentMessageLimit);
                        if (summarizeCount == 0)
                                return;
                        List<RagConversationMessage> messagesToSummarize = pending.subList(0, summarizeCount);
                        Map<String, Object> summaryRequest = Map.of(
                                        "current_summary", Objects.toString(conversation.getSummary(), ""),
                                        "new_messages", messagesToSummarize.stream()
                                                        .map(message -> Map.of("role", message.getRole(), "content",
                                                                        message.getContent()))
                                                        .toList());
                        SummaryResponse response = restTemplate.postForObject(
                                        ragUrl + "/summarize", summaryRequest, SummaryResponse.class);
                        if (response == null || response.summary() == null || response.summary().isBlank()) {
                                throw new IllegalStateException("Summary response is empty");
                        }
                        conversation.setSummary(response.summary().trim());
                        conversation.setLastSummarizedMessageId(
                                        messagesToSummarize.get(messagesToSummarize.size() - 1).getId());
                        conversation.setUpdatedAt(LocalDateTime.now());
                        conversationRepository.save(conversation);
                        log.info("Conversation summary updated conversationId={} userId={} courseId={} summarizedMessages={}",
                                        conversationId, userId, courseId, messagesToSummarize.size());
                } catch (RuntimeException exception) {
                        log.warn("Conversation summary update failed conversationId={} userId={} courseId={} errorType={}",
                                        conversationId, userId, courseId, exception.getClass().getSimpleName());
                }
        }

        private record SummaryResponse(String summary) {
        }
}
