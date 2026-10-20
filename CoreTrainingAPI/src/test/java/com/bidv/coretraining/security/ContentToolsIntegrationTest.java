package com.bidv.coretraining.security;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.*;
import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.location=classpath:access-test.yml",
        "spring.datasource.url=jdbc:h2:mem:content-tools;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
class ContentToolsIntegrationTest {
    static final Path storage;
    static {try{storage=Files.createTempDirectory("content-tools-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void paths(DynamicPropertyRegistry r){r.add("app.storage.documents-directory",()->storage.toString());}
    @MockBean org.springframework.mail.javamail.JavaMailSender mail;
    @MockBean NotificationWebSocketHandler socket;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserDetailsServiceImpl details;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    @Autowired QuestionRepository questions;
    @Autowired BankQuestionRepository bank;
    @Autowired LessonReportRepository reports;
    @Autowired NotificationRepository notifications;
    @Autowired QuestionBankService bankService;
    @Autowired LessonReportService reportService;
    @Autowired PlatformTransactionManager manager;
    String prefix;Long sourceQuestion;
    String id(String s){return prefix+s;}
    TransactionTemplate tx(){return new TransactionTemplate(manager);}
    @BeforeEach void setup(){
        prefix="CT-"+UUID.randomUUID().toString().substring(0,8)+"-";
        sourceQuestion=tx().execute(s->{
            var unit=units.save(OrganizationUnit.builder().id(id("A")).name("A").build());
            var other=units.save(OrganizationUnit.builder().id(id("B")).name("B").build());
            var domain=domains.save(TrainingDomain.builder().code(id("D")).name("Domain").build());
            var program=programs.save(TrainingProgram.builder().code(id("P")).name("Program").domain(domain).units(new HashSet<>(Set.of(unit))).build());
            Course source=courses.save(Course.builder().id(id("C")).code(id("C")).name("Source course").ownerUnit(unit).program(program).status("PUBLISHED").build());
            Course target=courses.save(Course.builder().id(id("T")).code(id("T")).name("Target course").ownerUnit(unit).program(program).status("DRAFT").build());
            Lesson first=lesson(source,id("L"),0),next=lesson(source,id("NEXT"),1),destination=lesson(target,id("TL"),0);
            first.ensureQuiz();lessons.saveAndFlush(first);
            var question=questions.saveAndFlush(Question.builder().course(source).lesson(first).quiz(first.getQuiz()).text("Original question").options(new ArrayList<>(List.of("A","B","C","D")))
                    .correctAnswer(0).correctAnswers(new ArrayList<>(List.of(0))).type(QuestionType.SINGLE_CHOICE).explanation("Original explanation").build());
            var learner=users.save(User.builder().username(id("learner")).fullName("Learner").password("unused").role("ROLE_USER").organizationUnit(unit).mustChangePassword(false).build());
            learner.getRegisteredCourses().add(source);
            users.save(User.builder().username(id("reviewer")).fullName("Reviewer").password("unused").role("ROLE_UNIT_ADMIN").organizationUnit(unit).mustChangePassword(false).build());
            users.save(User.builder().username(id("other")).fullName("Other admin").password("unused").role("ROLE_UNIT_ADMIN").organizationUnit(other).mustChangePassword(false).build());
            users.save(User.builder().username(id("global")).fullName("Global admin").password("unused").role("ROLE_ADMIN").mustChangePassword(false).build());
            return question.getId();
        });
        clearInvocations(socket);
    }
    Lesson lesson(Course c,String id,int order){
        Chapter chapter=c.getChapters().stream().findFirst().orElseGet(()->{var ch=chapters.save(Chapter.builder().course(c).code(id+"CH").title("Chapter").chapterOrder(0).status("PUBLISHED").build());c.getChapters().add(ch);return ch;});
        var l=Lesson.builder().id(id).title(id).course(c).chapter(chapter).lessonOrder(order).status("PUBLISHED").build();l.setTextBody("Content");lessons.save(l);chapter.getLessons().add(l);c.getLessons().add(l);return l;
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void as(String name){var p=(CustomUserDetails)details.loadUserByUsername(id(name));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    Cookie cookie(String name){as(name);return new Cookie("access_token",tokens.generateToken(SecurityContextHolder.getContext().getAuthentication()));}
    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) throws Exception {
        var result=mvc.perform(get("/api/auth/csrf")).andReturn();String token=json.readTree(result.getResponse().getContentAsString()).get("token").asText();
        return request.cookie(new Cookie("XSRF-TOKEN",token)).header("X-XSRF-TOKEN",token);
    }
    byte[] png() throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    BankQuestionDto request(String code,BankQuestion.Status status){return new BankQuestionDto(null,null,id(code),id("C"),null,id("L"),null,null,null,"Bank question",QuestionType.SINGLE_CHOICE,List.of("A","B","C","D"),List.of(0),"Explanation",BankQuestion.Difficulty.HARD,status,null,null);}
    BankQuestionDto edit(BankQuestionDto q,String text,BankQuestion.Status status){return new BankQuestionDto(q.id(),q.version(),q.code(),q.courseId(),null,q.lessonId(),null,null,null,text,q.type(),q.options(),q.correctAnswers(),q.explanation(),q.difficulty(),status,null,null);}

    @Test void learnerCanReportWithImageAndResponsibleAdminsReceiveCommittedNotifications() throws Exception {
        Cookie jwt=cookie("learner");
        var response=mvc.perform(csrf(multipart("/api/lessons/"+id("L")+"/reports").file(new MockMultipartFile("images","proof.png","image/png",png())).param("content","Nội dung sai cần kiểm tra").cookie(jwt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW")).andExpect(jsonPath("$.images.length()").value(1)).andReturn();
        long reportId=json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        tx().executeWithoutResult(s->{
            assertThat(notifications.findTop50ByRecipientUsernameOrderByCreatedAtDesc(id("global"))).anySatisfy(n->{assertThat(n.getType()).isEqualTo(NotificationType.LESSON_REPORT);assertThat(n.getTargetUrl()).endsWith("report="+reportId);});
            assertThat(notifications.findTop50ByRecipientUsernameOrderByCreatedAtDesc(id("reviewer"))).hasSize(1);
            assertThat(notifications.findTop50ByRecipientUsernameOrderByCreatedAtDesc(id("other"))).isEmpty();
        });
        verify(socket).sendToUser(eq(id("reviewer")),any());
        mvc.perform(get("/api/lesson-reports/"+reportId+"/images/0").cookie(jwt)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/lesson-reports/"+reportId+"/images/0").cookie(cookie("other"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/content-reports").cookie(cookie("learner"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/content-reports").cookie(cookie("reviewer"))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+reportId+")]").isNotEmpty());
    }

    @Test void reportValidationAndRollbackDoNotLeaveImagesOrNotifications() throws Exception {
        as("learner");long before=reports.count();
        assertThatThrownBy(()->reportService.submit(id("L")," ",List.of())).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        byte[] valid=png();
        assertThatThrownBy(()->reportService.submit(id("L"),"Report",List.of(new MockMultipartFile("images","ok.png","image/png",valid),new MockMultipartFile("images","bad.png","image/png","not an image".getBytes())))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        assertThat(reports.count()).isEqualTo(before);
        if(Files.exists(storage.resolve("lesson-reports")))try(var files=Files.list(storage.resolve("lesson-reports"))){assertThat(files.count()).isEqualTo(tx().execute(s->reports.findAll().stream().mapToLong(r->r.getImages().size()).sum()));}
        verify(socket,never()).sendToUser(eq(id("reviewer")),any());
    }

    @Test void reportRequiresAccessibleLessonAndLimitsImageCountAndSize() {
        as("learner");
        assertThatThrownBy(()->reportService.submit(id("NEXT"),"Locked lesson",List.of())).isInstanceOf(AccessDeniedException.class);
        var file=new MockMultipartFile("images","a.png","image/png",new byte[]{1});
        assertThatThrownBy(()->reportService.submit(id("L"),"Too many",List.of(file,file,file,file))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        assertThatThrownBy(()->reportService.submit(id("L"),"Too big",List.of(new MockMultipartFile("images","big.png","image/png",new byte[5*1024*1024+1])))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
    }

    @Test void reportReviewEnforcesScopeResolutionAndOptimisticVersion() {
        as("learner");var r=reportService.submit(id("L"),"Review content",List.of());
        as("other");assertThatThrownBy(()->reportService.review(r.id(),r.version(),LessonReport.Status.RESOLVED,"Fixed")).isInstanceOf(AccessDeniedException.class);
        as("reviewer");assertThatThrownBy(()->reportService.review(r.id(),r.version(),LessonReport.Status.RESOLVED,"")).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        var updated=reportService.review(r.id(),r.version(),LessonReport.Status.RESOLVED,"Đã sửa nội dung");
        assertThat(updated.status()).isEqualTo(LessonReport.Status.RESOLVED);assertThat(updated.reviewerName()).isEqualTo("Reviewer");
        assertThatThrownBy(()->reportService.review(r.id(),r.version(),LessonReport.Status.DISMISSED,"Stale")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void bankStoresRequestedMetadataAndRejectsDuplicateCodeAndStaleEdits() {
        as("reviewer");var q=bankService.save(null,request("Q1",BankQuestion.Status.ACTIVE));
        assertThat(q.domainName()).isEqualTo("Domain");assertThat(q.lessonId()).isEqualTo(id("L"));assertThat(q.difficulty()).isEqualTo(BankQuestion.Difficulty.HARD);assertThat(q.explanation()).isEqualTo("Explanation");
        assertThatThrownBy(()->bankService.save(null,request("Q1",BankQuestion.Status.ACTIVE))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        bankService.save(q.id(),edit(q,"Updated",BankQuestion.Status.ACTIVE));
        assertThatThrownBy(()->bankService.save(q.id(),edit(q,"Stale",BankQuestion.Status.ACTIVE))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void bankCaptureReusesExistingQuestionAndSnapshotSurvivesBankEditsAndArchival() {
        as("reviewer");var q=bankService.capture(sourceQuestion);
        assertThat(bankService.capture(sourceQuestion).id()).isEqualTo(q.id());
        assertThat(q.explanation()).isEqualTo("Original explanation");
        var copy=bankService.addToQuiz(id("TL"),List.of(q.id())).get(0);
        assertThat(copy.getBankQuestionId()).isEqualTo(q.id());assertThat(copy.getExplanation()).isEqualTo(q.explanation());
        bankService.save(q.id(),edit(q,"Changed bank content",BankQuestion.Status.ARCHIVED));
        tx().executeWithoutResult(s->{var stored=questions.findById(copy.getId()).orElseThrow();assertThat(stored.getText()).isEqualTo("Original question");assertThat(stored.getOptions()).containsExactly("A","B","C","D");});
    }

    @Test void bankImportIsAtomicAndRejectsInactiveDuplicatesAndPublishedTargets() {
        as("reviewer");var active=bankService.save(null,request("ACTIVE",BankQuestion.Status.ACTIVE));var inactive=bankService.save(null,request("DRAFT",BankQuestion.Status.DRAFT));
        assertThatThrownBy(()->bankService.addToQuiz(id("TL"),List.of(active.id(),inactive.id()))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        assertThat(questions.findByLessonId(id("TL"))).isEmpty();
        bankService.addToQuiz(id("TL"),List.of(active.id()));
        assertThatThrownBy(()->bankService.addToQuiz(id("TL"),List.of(active.id()))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        assertThatThrownBy(()->bankService.addToQuiz(id("L"),List.of(active.id()))).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
    }

    @Test void bankDoesNotExposeAnswersOrAllowImportsAcrossScope() throws Exception {
        as("reviewer");var q=bankService.save(null,request("SCOPED",BankQuestion.Status.ACTIVE));
        as("other");assertThat(bankService.list()).noneMatch(row->row.id().equals(q.id()));
        assertThatThrownBy(()->bankService.save(q.id(),edit(q,"Out of scope",BankQuestion.Status.ACTIVE))).isInstanceOf(AccessDeniedException.class);
        mvc.perform(get("/api/admin/question-bank").cookie(cookie("learner"))).andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/admin/question-bank/add-to-quiz").cookie(cookie("other")).contentType("application/json").content(json.writeValueAsString(Map.of("lessonId",id("TL"),"questionIds",List.of(q.id())))))).andExpect(status().isForbidden());
    }

    @Test void bankAndReportNavigationFollowExistingReadPermissions() throws Exception {
        mvc.perform(get("/api/navigation/me").cookie(cookie("reviewer"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'admin.question-bank')]").isNotEmpty()).andExpect(jsonPath("$[?(@.id == 'admin.content-reports')]").isNotEmpty());
        mvc.perform(get("/api/navigation/me").cookie(cookie("learner"))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == 'admin.question-bank')]").isEmpty());
    }
}
