package com.bidv.coretraining.security;
import com.bidv.coretraining.enums.VideoSource;
import com.bidv.coretraining.enums.DocumentType;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction and no OSIV: authentication and resource requests use
// separate persistence contexts, as they do on the running server.
@SpringBootTest(properties={"spring.config.location=classpath:access-test.yml",
        "spring.datasource.url=jdbc:h2:mem:detached-principal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
class DetachedPrincipalIntegrationTest {
    static final Path storage;
    static { try { storage=Files.createTempDirectory("detached-principal-"); }
        catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void paths(DynamicPropertyRegistry r) {
        r.add("app.storage.documents-directory",()->storage.toString());
        r.add("app.storage.videos-directory",()->storage.toString());
    }
    @MockBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockBean RagConversationSummaryService summaries;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    @Autowired AccessRoleRepository roles;
    @Autowired UserDetailsServiceImpl details;
    @Autowired JwtTokenProvider tokens;
    @Autowired AuthorizationService auth;
    @Autowired VideoAccessService videos;
    @Autowired RagChatService chat;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    String prefix;
    Long learnerId;

    TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    String id(String suffix) { return prefix+suffix; }

    @BeforeEach void setup() throws Exception {
        prefix="DP-"+UUID.randomUUID().toString().substring(0,8)+"-";
        learnerId=tx().execute(status->{
            var unit=units.save(OrganizationUnit.builder().id(id("U")).name("Unit").build());
            var domain=domains.save(TrainingDomain.builder().code(id("D")).name("Domain").build());
            var program=programs.save(TrainingProgram.builder().code(id("P")).name("Program").domain(domain).units(new HashSet<>(Set.of(unit))).build());
            var course=courses.save(Course.builder().id(id("C")).code(id("C")).name("Course").program(program).ownerUnit(unit).status("PUBLISHED").build());
            var chapter=chapters.save(Chapter.builder().code(id("CH")).title("Chapter").course(course).chapterOrder(0).status("PUBLISHED").build());
            course.getChapters().add(chapter);
            for(int n=0;n<2;n++) {
                var lesson=Lesson.builder().id(id("L"+n)).title("Lesson "+n).course(course).chapter(chapter).lessonOrder(n).status("PUBLISHED").build();
                lesson.setTextBody("Content");lesson.setVideoSource(VideoSource.UPLOAD);lesson.setVideoUrl("/uploads/videos/"+id("video.mp4"));
                lesson.getDocuments().add(LessonDocument.builder().lesson(lesson).name("PDF").documentType(DocumentType.PDF).fileUrl("/uploads/documents/"+id("file"+n+".pdf")).documentOrder(0).build());
                lessons.save(lesson);chapter.getLessons().add(lesson);course.getLessons().add(lesson);
            }
            var user=User.builder().username(id("learner")).fullName("Learner").password("unused").role("ROLE_USER").organizationUnit(unit).mustChangePassword(false).build();
            user.getRegisteredCourses().add(course);
            return users.save(user).getId();
        });
        Files.writeString(storage.resolve(id("file0.pdf")),"%PDF-1.4 test document");
        Files.writeString(storage.resolve(id("file1.pdf")),"%PDF-1.4 next document");
        Files.writeString(storage.resolve(id("video.mp4")),"test video bytes");
    }
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    CustomUserDetails principal(String username) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return (CustomUserDetails)details.loadUserByUsername(username);
    }
    UsernamePasswordAuthenticationToken authentication(CustomUserDetails principal) {
        return new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
    }
    Cookie cookie(String username) {
        return new Cookie("access_token",tokens.generateToken(authentication(principal(username))));
    }

    @Test void currentReloadsDetachedIdentityInCallerTransactionAndReadsLearningCollections() {
        var principal=principal(id("learner"));
        assertThat(Hibernate.isInitialized(principal.getUser().getRegisteredCourses())).isFalse();
        SecurityContextHolder.getContext().setAuthentication(authentication(principal));
        tx().executeWithoutResult(status->{
            User managed=auth.current();
            assertThat(managed).isNotSameAs(principal.getUser());
            assertThat(em.contains(managed)).isTrue();
            assertThat(managed.getRegisteredCourses()).hasSize(1);
            assertThat(managed.getCompletedLessons()).isEmpty();
            assertThat(managed.getQuizScores()).isEmpty();
            assertThat(managed.getCourseDeadlines()).isEmpty();
        });
        assertThat(Hibernate.isInitialized(principal.getUser().getRegisteredCourses())).isFalse();
    }

    @Test void documentAccessHonorsRegistrationSequenceAndRevocationWithoutSessionErrors() throws Exception {
        var jwt=cookie(id("learner"));
        String first="/uploads/documents/"+id("file0.pdf"),next="/uploads/documents/"+id("file1.pdf");
        mvc.perform(get(first).cookie(jwt)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get(next).cookie(jwt)).andExpect(status().isForbidden());
        tx().executeWithoutResult(s->users.findById(learnerId).orElseThrow().getCompletedLessons().add(lessons.findById(id("L0")).orElseThrow()));
        mvc.perform(get(next).cookie(jwt)).andExpect(status().isOk());
        tx().executeWithoutResult(s->users.findById(learnerId).orElseThrow().getRegisteredCourses().clear());
        mvc.perform(get(first).cookie(jwt)).andExpect(status().isForbidden());
        mvc.perform(get(first)).andExpect(status().isUnauthorized());
    }

    @Test void learningMetadataAndDiscussionKeepAccessChecksWithoutOpenSessionInView() throws Exception {
        var jwt = cookie(id("learner"));
        mvc.perform(get("/api/courses/" + id("C") + "/outline").cookie(jwt))
            .andExpect(status().isOk()).andExpect(jsonPath("$.course.lessons").isEmpty())
            .andExpect(jsonPath("$.chapters[0].lessons.length()").value(2))
            .andExpect(jsonPath("$.chapters[0].lessons[0].textBody").doesNotExist());
        mvc.perform(get("/api/lessons/" + id("L0")).cookie(jwt))
            .andExpect(status().isOk()).andExpect(jsonPath("$.textBody").value("Content"))
            .andExpect(jsonPath("$.commentCount").value(0));
        mvc.perform(get("/api/lessons/" + id("L0") + "/comments").cookie(jwt))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        for (String suffix : List.of("", "/comments", "/notes"))
            mvc.perform(get("/api/lessons/" + id("L1") + suffix).cookie(jwt)).andExpect(status().isForbidden());
        tx().executeWithoutResult(s -> users.findById(learnerId).orElseThrow().getRegisteredCourses().clear());
        mvc.perform(get("/api/lessons/" + id("L0") + "/comments").cookie(jwt)).andExpect(status().isForbidden());
        tx().executeWithoutResult(s -> {
            User user = users.findById(learnerId).orElseThrow();
            user.getRegisteredCourses().add(courses.findById(id("C")).orElseThrow());
        });
        mvc.perform(get("/api/courses/" + id("C") + "/outline").cookie(jwt)).andExpect(status().isOk());
        mvc.perform(get("/api/lessons/" + id("L0")).cookie(jwt)).andExpect(status().isOk());
    }

    @Test void stalePrincipalCannotKeepAccessAfterAccountIsLocked() {
        var principal=principal(id("learner"));
        SecurityContextHolder.getContext().setAuthentication(authentication(principal));
        tx().executeWithoutResult(s->users.findById(learnerId).orElseThrow().setStatus("LOCKED"));
        assertThat(principal.getUser().activeAccount()).isTrue();
        assertThatThrownBy(()->tx().execute(s->auth.current())).isInstanceOf(AccessDeniedException.class);
    }

    @Test void dynamicRoleOptionsAndMetadataWorkAfterAuthenticationTransactionCloses() throws Exception {
        tx().executeWithoutResult(s->{
            User user=users.findById(learnerId).orElseThrow();
            var role=roles.save(AccessRole.builder().code(id("ROLE")).name("Scoped instructor").permissions(new HashSet<>(Set.of("courses:read"))).unitIds(new HashSet<>(Set.of(id("U")))).build());
            user.setDynamicAccessInitialized(true);user.getAssignedRoles().add(role);
        });
        var principal=principal(id("learner"));
        assertThat(Hibernate.isInitialized(principal.getUser().getRegisteredCourses())).isFalse();
        assertThat(AccessPolicy.permissions(principal.getUser())).contains("courses:read");
        assertThat(AccessPolicy.roleNames(principal.getUser())).containsValue("Scoped instructor");
        mvc.perform(get("/api/admin/options/courses").cookie(cookie(id("learner")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[?(@.id == '"+id("C")+"')]").isNotEmpty())
                .andExpect(jsonPath("$.programs[?(@.code == '"+id("P")+"')]").isNotEmpty());
    }

    @Test void legacyAuthenticationMetadataDoesNotReadDetachedAssignedRoles() {
        var principal=principal(id("learner"));
        assertThat(Hibernate.isInitialized(principal.getUser().getAssignedRoles())).isFalse();
        assertThat(AccessPolicy.roleNames(principal.getUser())).isEmpty();
        assertThat(AccessPolicy.permissions(principal.getUser())).contains("LEARN");
    }

    @Test void streamPathKeepsTransactionAcrossInternalAuthorizationAndRejectsRevokedEnrollment() {
        String url=videos.grantAccess(id("L0"),id("learner")).streamUrl();
        String token=url.substring(url.indexOf("token=")+6);
        assertThat(videos.authorizeStreamPath(token)).isEqualTo(storage.resolve(id("video.mp4")));
        tx().executeWithoutResult(s->users.findById(learnerId).orElseThrow().getRegisteredCourses().clear());
        assertThatThrownBy(()->videos.authorizeStreamPath(token)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void chatChecksDetachedLearningStateInShortTransactionBeforeExternalRequest() {
        RestTemplate original=(RestTemplate)ReflectionTestUtils.getField(chat,"restTemplate");
        var http=mock(RestTemplate.class);
        when(http.postForObject(anyString(),any(),eq(RagServiceChatResponse.class))).thenAnswer(invocation->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new RagServiceChatResponse("Answer",true,List.of(),"Question",false,null);
        });
        ReflectionTestUtils.setField(chat,"restTemplate",http);
        try {
            assertThat(chat.chat(id("learner"),new RagChatRequest("Question",id("C"),null,null)).answer()).isEqualTo("Answer");
            tx().executeWithoutResult(s->users.findById(learnerId).orElseThrow().getRegisteredCourses().clear());
            assertThatThrownBy(()->chat.chat(id("learner"),new RagChatRequest("Question",id("C"),null,null))).isInstanceOf(AccessDeniedException.class);
            verify(http,times(1)).postForObject(anyString(),any(),eq(RagServiceChatResponse.class));
        } finally { ReflectionTestUtils.setField(chat,"restTemplate",original); }
    }
}
