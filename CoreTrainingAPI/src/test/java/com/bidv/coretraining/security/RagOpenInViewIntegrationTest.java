package com.bidv.coretraining.security;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.rag.dto.RagServiceChatResponse;
import com.bidv.coretraining.rag.service.RagChatService;
import com.bidv.coretraining.rag.service.RagConversationSummaryService;
import com.bidv.coretraining.training.entity.TrainingDomain;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.training.repository.TrainingDomainRepository;
import com.bidv.coretraining.training.repository.TrainingProgramRepository;
import com.bidv.coretraining.user.entity.OrganizationUnit;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.OrganizationUnitRepository;
import com.bidv.coretraining.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Open-in-view stays on (access-test.yml) for the application, but a chat request must not
// keep a persistence context, and with it a pooled connection, while it waits on the RAG service.
@SpringBootTest(properties={"spring.config.location=classpath:access-test.yml",
        "spring.datasource.url=jdbc:h2:mem:rag-open-in-view;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class RagOpenInViewIntegrationTest {
    @MockBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockBean RagConversationSummaryService summaries;
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired UserRepository users;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired CourseRepository courses;
    @Autowired UserDetailsServiceImpl details;
    @Autowired JwtTokenProvider tokens;
    @Autowired RagChatService chat;
    @Autowired PlatformTransactionManager transactions;
    String prefix;

    String id(String suffix) { return prefix+suffix; }

    @BeforeEach void setup() {
        prefix="OIV-"+UUID.randomUUID().toString().substring(0,8)+"-";
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            var unit=units.save(OrganizationUnit.builder().id(id("U")).name("Unit").build());
            var domain=domains.save(TrainingDomain.builder().code(id("D")).name("Domain").build());
            var program=programs.save(TrainingProgram.builder().code(id("P")).name("Program").domain(domain).units(new HashSet<>(Set.of(unit))).build());
            var course=courses.save(Course.builder().id(id("C")).code(id("C")).name("Course").program(program).ownerUnit(unit).status("PUBLISHED").build());
            var user=User.builder().username(id("learner")).fullName("Learner").password("unused").role("ROLE_USER").organizationUnit(unit).mustChangePassword(false).build();
            user.getRegisteredCourses().add(course);
            users.save(user);
        });
    }

    Cookie jwt() {
        var principal=(CustomUserDetails)details.loadUserByUsername(id("learner"));
        return new Cookie("access_token",tokens.generateToken(
                new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities())));
    }

    String ask(String conversationId) throws Exception {
        String body=conversationId==null
                ? "{\"question\":\"Question\",\"courseId\":\""+id("C")+"\"}"
                : "{\"question\":\"Next\",\"courseId\":\""+id("C")+"\",\"conversationId\":\""+conversationId+"\"}";
        return mvc.perform(post("/api/rag/chat").cookie(jwt(),new Cookie("XSRF-TOKEN","csrf"))
                        .header("X-XSRF-TOKEN","csrf").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Answer"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test void chatReleasesPersistenceContextWhileWaitingOnRag() throws Exception {
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).hasSize(1);
        RestTemplate original=(RestTemplate)ReflectionTestUtils.getField(chat,"restTemplate");
        var http=mock(RestTemplate.class);
        when(http.postForObject(anyString(),any(),eq(RagServiceChatResponse.class))).thenAnswer(invocation->{
            assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory)).isFalse();
            return new RagServiceChatResponse("Answer",true,List.of(),"Question",false,null);
        });
        ReflectionTestUtils.setField(chat,"restTemplate",http);
        try {
            String conversationId=com.jayway.jsonpath.JsonPath.read(ask(null),"$.conversationId");
            // A follow-up loads and updates the conversation with no request-wide session.
            ask(conversationId);
            verify(http,times(2)).postForObject(anyString(),any(),eq(RagServiceChatResponse.class));
        } finally { ReflectionTestUtils.setField(chat,"restTemplate",original); }
    }
}
