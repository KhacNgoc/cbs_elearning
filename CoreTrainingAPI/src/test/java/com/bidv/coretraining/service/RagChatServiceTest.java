package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.RagChatRequest;
import com.bidv.coretraining.entity.RagConversation;
import com.bidv.coretraining.entity.RagConversationMessage;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.RagConversationMessageRepository;
import com.bidv.coretraining.repository.RagConversationRepository;
import com.bidv.coretraining.repository.RagChatMessageRepository;
import com.bidv.coretraining.repository.TopicRepository;
import com.bidv.coretraining.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RagChatServiceTest {
    private UserRepository userRepository;
    private TopicRepository topicRepository;
    private RagConversationRepository conversationRepository;
    private RagConversationMessageRepository messageRepository;
    private RagChatMessageRepository legacyMessageRepository;
    private RagChatService service;
    private User currentUser;
    private Topic course;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        topicRepository = mock(TopicRepository.class);
        conversationRepository = mock(RagConversationRepository.class);
        messageRepository = mock(RagConversationMessageRepository.class);
        legacyMessageRepository = mock(RagChatMessageRepository.class);
        service = new RagChatService(
                userRepository, topicRepository, conversationRepository, messageRepository, legacyMessageRepository,
                "http://localhost:8001", 8, 12000, 12, 24000, 1000, 1000);
        currentUser = User.builder().id(1L).username("alice").role("ROLE_USER").build();
        course = Topic.builder().id("CIF").name("CIF").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(currentUser));
        when(topicRepository.findById("CIF")).thenReturn(Optional.of(course));
        when(userRepository.hasRegisteredTopic("alice", "CIF")).thenReturn(true);
    }

    @Test
    void rejectsConversationOwnedByAnotherUserBeforeReadingMessages() {
        RagConversation conversation = conversation(User.builder().id(2L).username("bob").build(), course);
        when(conversationRepository.findById("conversation-1")).thenReturn(Optional.of(conversation));

        assertThrows(AccessDeniedException.class, () -> service.chat(
                "alice", new RagChatRequest("Nó là gì?", "CIF", null, "conversation-1")));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void rejectsConversationFromAnotherCourse() {
        Topic otherCourse = Topic.builder().id("GL").name("GL").build();
        when(conversationRepository.findById("conversation-1"))
                .thenReturn(Optional.of(conversation(currentUser, otherCourse)));

        assertThrows(BadRequestException.class, () -> service.chat(
                "alice", new RagChatRequest("GL là gì?", "CIF", null, "conversation-1")));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void summaryFailureDoesNotDiscardSuccessfulAnswer() {
        service = new RagChatService(
                userRepository, topicRepository, conversationRepository, messageRepository, legacyMessageRepository,
                "http://localhost:8001", 2, 12000, 3, 24000, 1000, 1000);
        RagConversation conversation = conversation(currentUser, course);
        when(conversationRepository.findById("conversation-1")).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicLong ids = new AtomicLong(10);
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            RagConversationMessage message = invocation.getArgument(0);
            if (message.getId() == null) message.setId(ids.incrementAndGet());
            return message;
        });
        when(messageRepository.findTop30ByConversationIdAndIdLessThanOrderByIdDesc(anyString(), anyLong()))
                .thenReturn(List.of());
        when(messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(anyString(), anyLong()))
                .thenReturn(List.of(
                        conversationMessage(1L, conversation, "user", "CIF là gì?"),
                        conversationMessage(2L, conversation, "assistant", "CIF là hồ sơ."),
                        conversationMessage(3L, conversation, "user", "Nó được tạo khi nào?"),
                        conversationMessage(4L, conversation, "assistant", "Câu trả lời.")));

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://localhost:8001/chat"))
                .andRespond(withSuccess("""
                        {"answer":"Câu trả lời có căn cứ.","grounded":true,"sources":[],
                         "standaloneQuestion":"CIF được tạo khi nào?","needsClarification":false,"rewriteReason":""}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8001/summarize"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var response = service.chat(
                "alice", new RagChatRequest("Nó được tạo khi nào?", "CIF", null, "conversation-1"));

        server.verify();
        org.junit.jupiter.api.Assertions.assertEquals("Câu trả lời có căn cứ.", response.answer());
        org.junit.jupiter.api.Assertions.assertEquals("conversation-1", response.conversationId());
    }

    private RagConversation conversation(User owner, Topic topic) {
        return RagConversation.builder()
                .id("conversation-1")
                .user(owner)
                .topic(topic)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private RagConversationMessage conversationMessage(
            Long id, RagConversation conversation, String role, String content) {
        return RagConversationMessage.builder()
                .id(id)
                .conversation(conversation)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
