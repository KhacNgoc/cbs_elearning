package com.bidv.coretraining.service;
import com.bidv.coretraining.service.impl.*;

import com.bidv.coretraining.dto.RagChatRequest;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagChatErrorTest {
    @Test void preservesOutputAndTimeoutErrorsFromRag() {
        for (var status : new HttpStatus[]{HttpStatus.BAD_GATEWAY, HttpStatus.GATEWAY_TIMEOUT}) {
            var users = mock(UserRepository.class);
            var courses = mock(CourseRepository.class);
            var conversations = mock(RagConversationRepository.class);
            var messages = mock(RagConversationMessageRepository.class);
            when(users.findByUsername("admin")).thenReturn(Optional.of(
                    User.builder().id(1L).username("admin").role("ROLE_ADMIN").build()));
            when(courses.findById("C1")).thenReturn(Optional.of(Course.builder().id("C1").name("Cho vay").build()));
            when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(messages.save(any())).thenAnswer(invocation -> {
                RagConversationMessage message = invocation.getArgument(0);
                message.setId(1L);
                return message;
            });
            var service = new RagChatServiceImpl(users, courses, conversations, messages,
                    mock(RagChatMessageRepository.class), mock(RagConversationSummaryService.class),
                    mock(TrainingStructureService.class),
                    "http://rag", 8, 12000, 5000, 90000);
            var http = mock(RestTemplate.class);
            when(http.postForObject(anyString(), any(), eq(com.bidv.coretraining.dto.RagServiceChatResponse.class)))
                    .thenThrow(HttpServerErrorException.create(status, "error", HttpHeaders.EMPTY,
                            new byte[0], StandardCharsets.UTF_8));
            ReflectionTestUtils.setField(service, "restTemplate", http);
            assertThatThrownBy(() -> service.chat("admin", new RagChatRequest("câu hỏi", "C1", null, null)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode()).isEqualTo(status));
        }
    }
}
