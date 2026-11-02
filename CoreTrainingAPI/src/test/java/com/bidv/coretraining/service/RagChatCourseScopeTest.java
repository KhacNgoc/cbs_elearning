package com.bidv.coretraining.service;
import com.bidv.coretraining.service.impl.*;

import com.bidv.coretraining.dto.LearnerDashboardDto.CourseChoice;
import com.bidv.coretraining.dto.RagChatRequest;
import com.bidv.coretraining.dto.RagServiceChatResponse;
import com.bidv.coretraining.dto.RagSourceDto;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagChatCourseScopeTest {
    RagCourseScopeService scope = mock(RagCourseScopeService.class);
    RagConversationRepository conversations = mock(RagConversationRepository.class);
    RagConversationMessageRepository messages = mock(RagConversationMessageRepository.class);
    CourseRepository courses = mock(CourseRepository.class);
    RestTemplate http = mock(RestTemplate.class);
    RagChatServiceImpl service;

    @BeforeEach void setUp() {
        var users = mock(UserRepository.class);
        when(users.findByUsername("learner")).thenReturn(Optional.of(
                User.builder().id(7L).username("learner").role("ROLE_USER").build()));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> {
            RagConversationMessage message = invocation.getArgument(0);
            if (message.getId() == null) message.setId(1L);
            return message;
        });
        service = new RagChatServiceImpl(users, courses, conversations, messages,
                mock(RagChatMessageRepository.class), mock(RagConversationSummaryService.class),
                mock(TrainingStructureService.class), scope, "http://rag", 8, 12000, 5000, 90000);
        ReflectionTestUtils.setField(service, "restTemplate", http);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> sentRequest() {
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(http).postForObject(anyString(), captor.capture(), eq(RagServiceChatResponse.class));
        return (Map<String, Object>) captor.getValue();
    }

    @Test void searchesAllAccessibleCoursesWithoutChoosingOne() {
        when(scope.searchableCourses("learner")).thenReturn(List.of(
                new CourseChoice("C1", "Cho vay"), new CourseChoice("C2", "Tiền gửi")));
        when(http.postForObject(anyString(), any(), eq(RagServiceChatResponse.class))).thenReturn(
                new RagServiceChatResponse("Đáp án", true, List.of(
                        new RagSourceDto("D2", "tien-gui.pdf", 3, "Lãi suất", "C2", null),
                        new RagSourceDto("DX", "an.pdf", 1, "Ẩn", "HIDDEN", "Khóa ẩn")),
                        "q", false, ""));

        var response = service.chat("learner", new RagChatRequest("Lãi suất tiền gửi?", null, null, null));

        var request = sentRequest();
        assertThat(request).doesNotContainKey("course_id");
        assertThat((List<String>) request.get("course_ids")).containsExactly("C1", "C2");
        assertThat(response.sources()).singleElement().satisfies(source -> {
            assertThat(source.courseId()).isEqualTo("C2");
            assertThat(source.courseName()).isEqualTo("Tiền gửi");
        });
        var saved = ArgumentCaptor.forClass(RagConversation.class);
        verify(conversations, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getCourse()).isNull();
    }

    @Test void rejectsLearnerWithoutSearchableCourses() {
        when(scope.searchableCourses("learner")).thenReturn(List.of());
        assertThatThrownBy(() -> service.chat("learner", new RagChatRequest("Câu hỏi", null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(http);
    }

    @Test void conversationContinuesAcrossContextCourses() {
        var owner = User.builder().id(7L).username("learner").build();
        var existing = RagConversation.builder().id("CONV").user(owner)
                .course(Course.builder().id("C1").build()).summary("").build();
        when(conversations.findById("CONV")).thenReturn(Optional.of(existing));
        when(courses.findById("C2")).thenReturn(Optional.of(Course.builder().id("C2").name("Tiền gửi").build()));
        when(scope.searchableCourses("learner")).thenReturn(List.of(new CourseChoice("C1", "Cho vay")));
        when(http.postForObject(anyString(), any(), eq(RagServiceChatResponse.class))).thenReturn(
                new RagServiceChatResponse("Đáp án", false, List.of(), "q", false, ""));

        var response = service.chat("learner", new RagChatRequest("Câu hỏi", "C2", null, "CONV"));

        assertThat(response.conversationId()).isEqualTo("CONV");
        var request = sentRequest();
        assertThat(request.get("course_id")).isEqualTo("C2");
        assertThat((List<String>) request.get("course_ids")).containsExactly("C2", "C1");
    }
}
