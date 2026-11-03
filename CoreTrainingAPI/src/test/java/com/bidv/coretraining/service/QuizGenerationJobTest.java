package com.bidv.coretraining.service;
import com.bidv.coretraining.service.impl.*;

import com.bidv.coretraining.dto.QuizGenerationJobDto;
import com.bidv.coretraining.dto.QuizGenerationRequestDto;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.mapper.QuizMapper;
import com.bidv.coretraining.repository.BankQuestionRepository;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QuizGenerationJobTest {
    static final String QUEUED = "{\"job_id\":\"abc123\",\"status\":\"queued\",\"stage\":\"queued\",\"message\":\"Đang chờ xử lý\","
            + "\"percent\":0,\"requested_questions\":3,\"accepted_questions\":0,\"attempt\":0,\"max_attempts\":2}";
    CourseRepository courses = mock(CourseRepository.class);
    LessonKnowledgeSourceService sources = mock(LessonKnowledgeSourceService.class);
    QuizGenerationService service = new QuizGenerationServiceImpl(courses, mock(LessonRepository.class), sources,
            mock(QuestionRepository.class), mock(BankQuestionRepository.class), "http://rag", 5000, 30000);
    MockRestServiceServer server = MockRestServiceServer.bindTo(
            (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();

    QuizGenerationRequestDto request() {
        var request = new QuizGenerationRequestDto();
        request.setCourseId("C1");
        request.setSingleChoiceCount(3);
        when(courses.findById("C1")).thenReturn(Optional.of(Course.builder().id("C1").name("Cho vay").build()));
        return request;
    }

    void started() {
        server.expect(requestTo("http://rag/quiz/jobs")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.course_id").value("C1"))
                .andExpect(jsonPath("$.single_choice_count").value(3))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(QUEUED));
        service.startJob(request());
        server.verify();
        server.reset();
    }

    @Test void startJobSendsSameRagPayloadAndRemembersCourse() {
        started();
        server.verify();
        assertThat(service.jobCourseId("abc123")).isEqualTo("C1");
    }

    @Test void statusMapsProgressAndFinalResult() {
        started();
        server.expect(requestTo("http://rag/quiz/jobs/abc123")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"job_id\":\"abc123\",\"status\":\"completed\",\"stage\":\"completed\",\"message\":\"Đã tạo xong\","
                        + "\"percent\":100,\"requested_questions\":3,\"accepted_questions\":1,\"attempt\":2,\"max_attempts\":2,"
                        + "\"phase\":2,\"phase_count\":2,\"completed_steps\":1,\"total_steps\":1,\"result\":{\"questions\":[{\"type\":\"SINGLE_CHOICE\",\"text\":\"Q\","
                        + "\"options\":[\"A\",\"B\",\"C\"],\"correct_answers\":[0],\"explanation\":\"\",\"source_ids\":[\"SOURCE_1\"]}],"
                        + "\"sources\":[],\"warnings\":[\"Chỉ tạo được 1/3\"]}}", MediaType.APPLICATION_JSON));
        QuizGenerationJobDto job = service.jobStatus("abc123");
        assertThat(job.getPercent()).isEqualTo(100);
        assertThat(job.getAcceptedQuestions()).isEqualTo(1);
        assertThat(job.getRequestedQuestions()).isEqualTo(3);
        assertThat(job.getPhase()).isEqualTo(2);
        assertThat(job.getPhaseCount()).isEqualTo(2);
        assertThat(job.getCompletedSteps()).isEqualTo(1);
        assertThat(job.getResult().getQuestions().get(0).getCorrectAnswers()).containsExactly(0);
        assertThat(job.getResult().getWarnings()).containsExactly("Chỉ tạo được 1/3");
    }

    @Test void failedJobGetsLocalizedErrorWithoutRawRagDetail() {
        started();
        server.expect(requestTo("http://rag/quiz/jobs/abc123"))
                .andRespond(withSuccess("{\"job_id\":\"abc123\",\"status\":\"failed\",\"stage\":\"failed\",\"percent\":40,"
                        + "\"error\":\"Quiz generation model is unavailable\",\"error_status\":503}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://rag/quiz/jobs/abc123"))
                .andRespond(withSuccess("{\"job_id\":\"abc123\",\"status\":\"failed\",\"stage\":\"failed\",\"percent\":40,"
                        + "\"error\":\"timeout\",\"error_status\":504}", MediaType.APPLICATION_JSON));
        assertThat(service.jobStatus("abc123").getError()).isEqualTo("Dịch vụ tạo câu hỏi tạm thời không khả dụng");
        assertThat(service.jobStatus("abc123").getError()).startsWith("Quá thời gian tạo câu hỏi");
    }

    @Test void expiredRagJobIsNotFoundAndForgotten() {
        started();
        server.expect(requestTo("http://rag/quiz/jobs/abc123")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.jobStatus("abc123")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.jobCourseId("abc123")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void unknownJobNeverCallsRag() {
        assertThatThrownBy(() -> service.jobStatus("nope")).isInstanceOf(ResourceNotFoundException.class);
        server.verify();
    }

    @Test void ragOutageOnStartIsUnavailable() {
        server.expect(requestTo("http://rag/quiz/jobs")).andRespond(withServerError());
        assertThatThrownBy(() -> service.startJob(request())).isInstanceOf(RagServiceUnavailableException.class);
        assertThatThrownBy(() -> service.jobCourseId("abc123")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void pollingAuthorizesAgainstTheJobsCourse() {
        var authorization = mock(AuthorizationService.class);
        var generation = mock(QuizGenerationService.class);
        var admin = new QuizAdminServiceImpl(authorization, courses, mock(LessonRepository.class), mock(QuestionRepository.class),
                mock(QuizMapper.class), mock(TrainingStructureService.class), generation);
        when(generation.jobCourseId("abc123")).thenReturn("C9");
        when(generation.jobStatus("abc123")).thenReturn(new QuizGenerationJobDto());
        admin.getQuizGenerationJob("abc123");
        var inOrder = inOrder(authorization, generation);
        inOrder.verify(authorization).edit("C9");
        inOrder.verify(generation).jobStatus("abc123");

        var request = request();
        admin.startQuizGenerationJob(request);
        verify(authorization).edit("C1");
        verify(generation).startJob(request);
    }
}
