package com.bidv.coretraining.service;
import com.bidv.coretraining.enums.DocumentStatus;

import com.bidv.coretraining.dto.LessonKnowledgeSourceDto;
import com.bidv.coretraining.service.impl.LessonKnowledgeSourceServiceImpl;
import org.springframework.context.annotation.Bean;
import static org.mockito.Mockito.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.profiles.active=test", "spring.application.name=knowledge-source-tests"})
@ContextConfiguration(classes = LessonKnowledgeSourceServiceTest.Config.class)
class LessonKnowledgeSourceServiceTest {
    @org.springframework.context.annotation.Configuration @EnableAutoConfiguration
    @EntityScan("com.bidv.coretraining.entity")
    @EnableJpaRepositories("com.bidv.coretraining.repository")
    @Import(LessonKnowledgeSourceServiceImpl.class)
    static class Config {
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
    }

    static final HttpServer stub = startStub();
    static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/documents/", exchange -> {
                byte[] body = """
                        {"documentId":"doc","pages":[1,2,3,5],"sections":["Giải ngân","Phê duyệt"],"excerpts":[]}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.rag-url", () -> "http://127.0.0.1:" + stub.getAddress().getPort());
    }
    @AfterAll static void stop() { stub.stop(0); }

    @Autowired LessonKnowledgeSourceService service;
    @Autowired AuthorizationService authorization;
    @Autowired LessonKnowledgeSourceRepository sources;
    @Autowired LessonRepository lessons;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired CourseDocumentRepository documents;
    @Autowired jakarta.persistence.EntityManager em;
    Lesson lesson;
    Course course;

    @BeforeEach void fixture() {
        reset(authorization);
        TrainingDomain domain = domains.save(TrainingDomain.builder().code("TD").name("Tín dụng").build());
        TrainingProgram program = programs.save(TrainingProgram.builder().domain(domain).code("P").name("Cho vay").build());
        course = courses.save(Course.builder().id("C1").code("C1").name("Cho vay").program(program).build());
        Chapter chapter = chapters.save(Chapter.builder().course(course).code("CH1").title("Cho vay").build());
        lesson = lessons.save(Lesson.builder().id("L1").course(course).chapter(chapter).title("Giải ngân").build());
        documents.save(CourseDocument.builder().id("doc").course(course).fileName("guide.pdf")
                .storagePath("/tmp/guide.pdf").contentType("application/pdf").sizeBytes(100)
                .status(DocumentStatus.INDEXED).createdAt(LocalDateTime.now()).build());
        em.flush();
    }

    @Test void authorizedEntriesUseTheSamePersistenceOperations() {
        var selection = new LessonKnowledgeSourceDto("doc", 2, 3, null);
        assertThat(service.saveAuthorized("L1", List.of(selection))).containsExactly(selection);
        em.clear();
        assertThat(service.listAuthorized("L1")).containsExactly(selection);
        assertThat(service.outlineAuthorized("C1", "doc").pages()).contains(2, 3);
        verify(authorization, times(2)).edit(any(Course.class));
        verify(authorization).edit("C1");
    }

    @Test void unauthorizedSaveCannotReplaceExistingSources() {
        var selection = new LessonKnowledgeSourceDto("doc", 2, 3, null);
        service.save("L1", List.of(selection));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(authorization).edit(any(Course.class));
        assertThatThrownBy(() -> service.saveAuthorized("L1", List.of()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(service.list("L1")).containsExactly(selection);
    }

    @Test void missingLessonRetainsControllerLookupErrorBeforeAuthorization() {
        assertThatThrownBy(() -> service.listAuthorized("missing"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verifyNoInteractions(authorization);
    }

    @Test void savesReplacesAndReloadsRangesWithoutReembedding() {
        var selection = new LessonKnowledgeSourceDto("doc", 2, 3, null);
        assertThat(service.save("L1", List.of(selection))).containsExactly(selection);
        em.clear();
        assertThat(service.list("L1")).containsExactly(selection);
        var replacement = new LessonKnowledgeSourceDto("doc", 5, 5, null);
        service.save("L1", List.of(replacement));
        em.clear();
        assertThat(service.list("L1")).containsExactly(replacement);
        assertThat(sources.count()).isEqualTo(1);
        assertThat(service.generationSources(lessons.findById("L1").orElseThrow()).get(0))
                .containsEntry("document_id", "doc").containsEntry("page_from", 5).containsEntry("page_to", 5);
    }

    @Test void rejectsLessonWithoutSourcesAndAfterClearingSources() {
        assertThatThrownBy(() -> service.generationSources(lesson)).isInstanceOf(BadRequestException.class);
        service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", null, null, null)));
        service.save("L1", List.of());
        assertThat(service.list("L1")).isEmpty();
        assertThatThrownBy(() -> service.generationSources(lesson)).isInstanceOf(BadRequestException.class);
    }

    @Test void rejectsInvalidOrEmptyPdfRanges() {
        for (var source : List.of(new LessonKnowledgeSourceDto("doc", 3, 2, null),
                new LessonKnowledgeSourceDto("doc", 1, 6, null),
                new LessonKnowledgeSourceDto("doc", 4, 4, null),
                new LessonKnowledgeSourceDto("doc", 1, null, null),
                new LessonKnowledgeSourceDto("doc", 1, 2, "Giải ngân"))) {
            assertThatThrownBy(() -> service.save("L1", List.of(source))).isInstanceOf(BadRequestException.class);
        }
        assertThat(sources.count()).isZero();
    }

    @Test void rejectsUnreadyAndForeignCourseDocuments() {
        CourseDocument document = documents.findById("doc").orElseThrow();
        document.setStatus(DocumentStatus.INDEXING);
        assertThatThrownBy(() -> service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", null, null, null))))
                .isInstanceOf(BadRequestException.class);
        document.setStatus(DocumentStatus.INDEXED);
        Course other = courses.save(Course.builder().id("C2").code("C2").name("Khác").program(course.getProgram()).build());
        document.setCourse(other);
        assertThatThrownBy(() -> service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", null, null, null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void restrictsWordSelectionsToRealSections() {
        documents.findById("doc").orElseThrow().setFileName("guide.docx");
        var selection = new LessonKnowledgeSourceDto("doc", null, null, "Giải ngân");
        assertThat(service.save("L1", List.of(selection))).containsExactly(selection);
        assertThatThrownBy(() -> service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", null, null, "Không tồn tại"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", 1, 1, null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void rejectsPublishedCourseChangesButRetainsSourcesForGeneration() {
        service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", 2, 3, null)));
        course.setStatus("PUBLISHED");
        assertThat(service.generationSources(lesson)).hasSize(1);
        assertThatThrownBy(() -> service.save("L1", List.of())).isInstanceOf(BadRequestException.class);
    }

    @Test void deletingLessonCascadesSourceAssignments() {
        service.save("L1", List.of(new LessonKnowledgeSourceDto("doc", null, null, null)));
        lessons.delete(lesson);
        em.flush();
        assertThat(sources.count()).isZero();
        assertThat(documents.count()).isEqualTo(1);
    }
}
