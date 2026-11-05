package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.service.LessonKnowledgeSourceService;
import com.bidv.coretraining.quiz.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.quiz.repository.BankQuestionRepository;
import com.bidv.coretraining.quiz.repository.QuestionRepository;
import com.bidv.coretraining.quiz.service.impl.QuizGenerationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QuizGenerationSourceTest {
    CourseRepository courses = mock(CourseRepository.class);
    LessonRepository lessons = mock(LessonRepository.class);
    LessonKnowledgeSourceService sources = mock(LessonKnowledgeSourceService.class);
    QuestionRepository questions = mock(QuestionRepository.class);
    BankQuestionRepository bank = mock(BankQuestionRepository.class);
    QuizGenerationService service = new QuizGenerationServiceImpl(courses, lessons, sources, questions, bank,
            "http://rag", 5000, 30000, 10);
    Course course = Course.builder().id("C1").name("Cho vay").build();
    Lesson lesson = Lesson.builder().id("L1").course(course).title("Giải ngân").build();
    QuizGenerationRequestDto request() {
        var request = new QuizGenerationRequestDto();
        request.setCourseId("C1");
        request.setLessonId("L1");
        when(courses.findById("C1")).thenReturn(Optional.of(course));
        when(lessons.findById("L1")).thenReturn(Optional.of(lesson));
        return request;
    }
    MockRestServiceServer server() {
        return MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
    }
    @Test void sendsSavedSourcesAndLessonIdentityToRag() {
        var request = request();
        when(sources.generationSources(lesson)).thenReturn(List.of(Map.of("document_id", "D1", "page_from", 2, "page_to", 3)));
        var server = server();
        server.expect(requestTo("http://rag/quiz/generate"))
                .andExpect(jsonPath("$.course_id").value("C1"))
                .andExpect(jsonPath("$.lesson_id").value("L1"))
                .andExpect(jsonPath("$.lesson_name").value("Giải ngân"))
                .andExpect(jsonPath("$.sources[0].document_id").value("D1"))
                .andExpect(jsonPath("$.sources[0].page_from").value(2))
                .andExpect(jsonPath("$.sources[0].page_to").value(3))
                .andRespond(withSuccess("{\"questions\":[],\"sources\":[],\"warnings\":[]}", MediaType.APPLICATION_JSON));
        service.generate(request);
        server.verify();
    }
    @Test void sendsDraftsSavedAndBankQuestionsOnceAsExistingQuestions() {
        var request = request();
        request.setExcludeQuestions(List.of("Câu nháp A", "  ", "Câu đã lưu B"));
        when(sources.generationSources(lesson)).thenReturn(List.of(Map.of("document_id", "D1")));
        when(questions.findTextsByLessonId("L1")).thenReturn(List.of("Câu đã lưu B", "Câu đã lưu C"));
        when(bank.findRecentTextsByLessonId(eq("L1"), any())).thenReturn(List.of("Câu ngân hàng D"));
        var server = server();
        server.expect(requestTo("http://rag/quiz/generate"))
                .andExpect(jsonPath("$.existing_questions.length()").value(4))
                .andExpect(jsonPath("$.existing_questions[0]").value("Câu nháp A"))
                .andExpect(jsonPath("$.existing_questions[1]").value("Câu đã lưu B"))
                .andExpect(jsonPath("$.existing_questions[3]").value("Câu ngân hàng D"))
                .andRespond(withSuccess("{\"questions\":[],\"sources\":[],\"warnings\":[]}", MediaType.APPLICATION_JSON));
        service.generate(request);
        server.verify();
        verify(questions, never()).findCourseQuizTexts(any());
    }
    @Test void missingLessonSourcesNeverCallsRag() {
        var request = request();
        when(sources.generationSources(lesson)).thenThrow(new BadRequestException("Chưa có nguồn"));
        var server = server();
        assertThatThrownBy(() -> service.generate(request)).isInstanceOf(BadRequestException.class);
        server.verify();
    }
    @Test void foreignCourseLessonIsRejectedBeforeSourcesOrRag() {
        var request = request();
        lesson.setCourse(Course.builder().id("OTHER").build());
        assertThatThrownBy(() -> service.generate(request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(sources);
    }
    @Test void courseQuizDoesNotRequireLessonAssignments() {
        var request = request();
        request.setLessonId(null);
        var server = server();
        server.expect(requestTo("http://rag/quiz/generate"))
                .andExpect(jsonPath("$.course_id").value("C1"))
                .andExpect(jsonPath("$.sources").doesNotExist())
                .andExpect(jsonPath("$.lesson_id").doesNotExist())
                .andRespond(withSuccess("{\"questions\":[],\"sources\":[],\"warnings\":[]}", MediaType.APPLICATION_JSON));
        service.generate(request);
        server.verify();
        verifyNoInteractions(sources);
    }
}
