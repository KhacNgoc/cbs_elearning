package com.bidv.coretraining.security;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.config.location=classpath:access-test.yml")
@AutoConfigureMockMvc @Transactional
class AccessSecurityIntegrationTest {
    static final Path storage;
    static {try{storage=Files.createTempDirectory("access-security-");}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void paths(DynamicPropertyRegistry r){r.add("app.storage.documents-directory",()->storage.toString());r.add("app.storage.videos-directory",()->storage.toString());}
    @org.springframework.boot.test.mock.mockito.MockBean org.springframework.mail.javamail.JavaMailSender sender;
    @Autowired PasswordResetOtpRepository otps;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired UserRepository users;
    @Autowired OrganizationUnitRepository units;
    @Autowired TrainingDomainRepository domains;
    @Autowired TrainingProgramRepository programs;
    @Autowired CourseRepository courses;
    @Autowired ChapterRepository chapters;
    @Autowired LessonRepository lessons;
    OrganizationUnit a,b,child;
    Course ca,cb;
    Lesson la,lb;
    User admin,ua,ub,instructor,learner,viewer;
    final String password="Test-Password-123";

    @BeforeEach void setup() throws Exception {
        a=units.save(OrganizationUnit.builder().id("A").name("Unit A").build());
        b=units.save(OrganizationUnit.builder().id("B").name("Unit B").build());
        child=units.save(OrganizationUnit.builder().id("A1").name("Department A1").parent(a).type("DEPARTMENT").build());
        var d=domains.save(TrainingDomain.builder().code("DOMAIN").name("Domain").build());
        var p=programs.save(TrainingProgram.builder().code("PROGRAM").name("Program").domain(d).build());
        ca=courses.save(Course.builder().id("CA").code("CA").name("Course A").ownerUnit(a).program(p).status("PUBLISHED").build());
        cb=courses.save(Course.builder().id("CB").code("CB").name("Course B").ownerUnit(b).program(p).status("PUBLISHED").build());
        la=lesson(ca,"LA");lb=lesson(cb,"LB");
        admin=user("global","ROLE_ADMIN",null);ua=user("admin-a","ROLE_UNIT_ADMIN",a);ub=user("admin-b","ROLE_UNIT_ADMIN",b);
        instructor=user("instructor","ROLE_INSTRUCTOR",a);learner=user("learner","ROLE_USER",a);viewer=user("viewer","ROLE_REPORT_VIEWER",a);
        ca.getInstructorIds().add(instructor.getId());courses.save(ca);
        learner.getRegisteredCourses().add(ca);users.save(learner);
        la.getDocuments().add(LessonDocument.builder().lesson(la).name("Protected").documentType(DocumentType.PDF).fileUrl("/uploads/documents/protected.pdf").documentOrder(0).build());lessons.saveAndFlush(la);
        Files.writeString(storage.resolve("protected.pdf"),"protected-data");
    }
    User user(String name,String role,OrganizationUnit unit){return users.save(User.builder().username(name).fullName(name).password(passwords.encode(password)).role(role).organizationUnit(unit).email(name+"@example.vn").mustChangePassword(false).build());}
    Lesson lesson(Course c,String id){var chapter=chapters.save(Chapter.builder().code(id).title(id).course(c).status("PUBLISHED").build());c.getChapters().add(chapter);var l=Lesson.builder().id(id).title(id).course(c).chapter(chapter).status("PUBLISHED").build();l.ensureContent();l.setTextBody("Content");chapter.getLessons().add(l);c.getLessons().add(l);return lessons.save(l);}
    ResultActions write(MockHttpServletRequestBuilder request,Cookie jwt,Object body) throws Exception {
        MvcResult csrf=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token=json.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        request.cookie(new Cookie("XSRF-TOKEN",token)).header("X-XSRF-TOKEN",token);
        if(jwt!=null)request.cookie(jwt);
        if(body!=null)request.contentType("application/json").content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    Cookie login(User u) throws Exception {
        var result=write(post("/api/auth/login"),null,Map.of("username",u.getUsername(),"password",password)).andExpect(status().isOk()).andReturn();
        Cookie cookie=result.getResponse().getCookie("access_token");assertThat(cookie).isNotNull();return cookie;
    }
    Map<String,Object> userBody(User u){Map<String,Object> r=new HashMap<>();r.put("username",u.getUsername());r.put("fullName",u.getFullName());r.put("role",u.getRole());r.put("roles",u.effectiveRoles());r.put("unitId",u.getOrganizationUnit()==null?null:u.getOrganizationUnit().getId());r.put("status",u.getStatus());r.put("points",0);return r;}

    @Test void learnerWorkspaceUsesOnlyVisibleCoursesAndRestoresDocumentAndVideoBookmarks() throws Exception {
        Cookie jwt=login(learner);
        mvc.perform(get("/api/users/me/learning").cookie(jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1)).andExpect(jsonPath("$.courses[0].course.id").value("CA"))
                .andExpect(jsonPath("$.courses[0].learningStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.courses[0].totalCount").value(1));
        write(post("/api/users/me/lessons/LA/progress"),jwt,Map.of("contentType","VIDEO","lastPositionSeconds",754,"watchedSeconds",20,"durationSeconds",1200))
                .andExpect(status().isOk());
        Long doc=la.getDocuments().get(0).getId();
        write(post("/api/users/me/lessons/LA/progress"),jwt,Map.of("contentType","DOCUMENT","documentId",doc,"documentPage",7,"scrollOffset",100))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastPositionSeconds").value(754.0)).andExpect(jsonPath("$.documentPage").value(7));
        mvc.perform(get("/api/users/me/learning").cookie(jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].learningStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.courses[0].percent").value(0))
                .andExpect(jsonPath("$.resume.contentType").value("DOCUMENT")).andExpect(jsonPath("$.resume.documentPage").value(7));
        write(post("/api/users/me/lessons/LA/progress"),jwt,Map.of("contentType","DOCUMENT","documentId",999999,"documentPage",1)).andExpect(status().isBadRequest());
        write(post("/api/users/me/lessons/LA/progress"),jwt,Map.of("contentType","DOCUMENT","documentId",doc,"documentPage",0)).andExpect(status().isBadRequest());
    }
    @Test void individualDeadlineOverridesCourseDefaultAndCannotBeChangedAcrossScope() throws Exception {
        ca.setCompletionDeadline(java.time.LocalDate.of(2026,10,30));courses.saveAndFlush(ca);
        Cookie owner=login(ua);
        write(put("/api/admin/users/"+learner.getId()+"/course-deadlines"),owner,Map.of("courseId","CA","dueDate","2026-10-20"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/learning").cookie(login(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].dueDate").value("2026-10-20"));
        write(put("/api/admin/users/"+learner.getId()+"/course-deadlines"),login(ub),Map.of("courseId","CA","dueDate","2026-10-01"))
                .andExpect(status().isForbidden());
        Map<String,Object> reset=new HashMap<>();reset.put("courseId","CA");reset.put("dueDate",null);
        write(put("/api/admin/users/"+learner.getId()+"/course-deadlines"),owner,reset).andExpect(status().isOk());
        mvc.perform(get("/api/users/me/learning").cookie(login(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].dueDate").value("2026-10-30"));
    }
    byte[] imageBytes() throws Exception {
        var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(12,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();
    }
    @Test void courseCreationPersistsImageAndFilterMetadata() throws Exception {
        Cookie jwt=login(admin);
        var file=new org.springframework.mock.web.MockMultipartFile("file","cover.jpg","image/jpeg",imageBytes());
        var image=write(multipart("/api/admin/courses/images").file(file).param("programId",ca.getProgram().getId()),jwt,null)
                .andExpect(status().isOk()).andReturn();
        String thumbnail=json.readTree(image.getResponse().getContentAsString()).get("thumbnail").asText();
        write(post("/api/admin/courses"),jwt,Map.of("code","NEW-COURSE","name","New course","programId",ca.getProgram().getId(),
                "thumbnail",thumbnail,"businessModule","LN","level","Cơ bản","keywords","giải ngân","completionDeadline","2026-11-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.thumbnail").value(thumbnail))
                .andExpect(jsonPath("$.businessModule").value("LN")).andExpect(jsonPath("$.level").value("Cơ bản"))
                .andExpect(jsonPath("$.completionDeadline").value("2026-11-15"));
    }
    @Test void courseImageUploadChecksPermissionsSignatureAndServingScope() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("file","cover.png","image/png",imageBytes());
        write(multipart("/api/admin/courses/images").file(file).param("courseId","CA"),login(learner),null).andExpect(status().isForbidden());
        write(multipart("/api/admin/courses/images").file(file).param("courseId","CB"),login(instructor),null).andExpect(status().isForbidden());
        var bad=new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","not an image".getBytes());
        write(multipart("/api/admin/courses/images").file(bad).param("courseId","CA"),login(instructor),null).andExpect(status().isBadRequest());
        var uploaded=write(multipart("/api/admin/courses/images").file(file).param("courseId","CA"),login(instructor),null)
                .andExpect(status().isOk()).andReturn();
        String path=json.readTree(uploaded.getResponse().getContentAsString()).get("thumbnail").asText();
        ca.setThumbnail(path);courses.saveAndFlush(ca);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).cookie(login(ub))).andExpect(status().isForbidden());
        mvc.perform(get(path).cookie(login(learner))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    }

    @Test void learnerCannotCallAdminOrWriteForeignDiscussion() throws Exception {
        Cookie jwt=login(learner);
        mvc.perform(get("/api/admin/users").cookie(jwt)).andExpect(status().isForbidden());
        write(post("/api/lessons/LB/comments"),jwt,Map.of("content","Unauthorized","rating",5)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lessons/LB").cookie(jwt)).andExpect(status().isForbidden());
    }
    @Test void scopedAdminCannotReadOrEditForeignUsersOrGrantGlobalRole() throws Exception {
        Cookie jwt=login(ua);
        String list=mvc.perform(get("/api/admin/users").cookie(jwt)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("admin-a").doesNotContain("admin-b","global");
        write(put("/api/admin/users/"+ub.getId()),jwt,userBody(ub)).andExpect(status().isForbidden());
        var promotion=userBody(learner);promotion.put("roles",Set.of("ROLE_ADMIN"));
        write(put("/api/admin/users/"+learner.getId()),jwt,promotion).andExpect(status().isForbidden());
    }
    @Test void scopedAdminCannotExpandScopeOrMoveUserOutsideUnit() throws Exception {
        Cookie jwt=login(ua);var body=userBody(learner);body.put("scopeUnitIds",Set.of("B"));
        write(put("/api/admin/users/"+learner.getId()),jwt,body).andExpect(status().isForbidden());
        body.remove("scopeUnitIds");body.put("unitId","B");write(put("/api/admin/users/"+learner.getId()),jwt,body).andExpect(status().isForbidden());
    }
    @Test void instructorCanPreviewOnlyAssignedCourseAndCannotManageAccounts() throws Exception {
        Cookie jwt=login(instructor);
        mvc.perform(get("/api/lessons/LA").cookie(jwt)).andExpect(status().isOk());
        mvc.perform(get("/api/lessons/LB").cookie(jwt)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/courses/CB/documents").cookie(jwt)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(jwt)).andExpect(status().isForbidden());
        write(post("/api/quizzes/lessons/LA/submit"),jwt,Map.of()).andExpect(status().isForbidden());
        write(put("/api/admin/lessons/LB"),jwt,Map.of("title","stolen")).andExpect(status().isForbidden());
    }
    @Test void reportViewerIsReadOnlyAndHasDistinctRole() throws Exception {
        Cookie jwt=login(viewer);
        mvc.perform(get("/api/auth/session").cookie(jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_REPORT_VIEWER"));
        mvc.perform(get("/api/admin/courses").cookie(jwt)).andExpect(status().isForbidden());
        write(post("/api/users/me/register-course/CA"),jwt,null).andExpect(status().isForbidden());
    }
    @Test void protectedDocumentRejectsAnonymousAndForeignAccounts() throws Exception {
        mvc.perform(get("/uploads/documents/protected.pdf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/uploads/documents/protected.pdf").cookie(login(ub))).andExpect(status().isForbidden());
        mvc.perform(get("/uploads/documents/protected.pdf").cookie(login(learner))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void logoutAndLockInvalidateExistingTokens() throws Exception {
        Cookie jwt=login(learner);write(post("/api/auth/logout"),jwt,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/session").cookie(jwt)).andExpect(status().isUnauthorized());
        Cookie another=login(learner);var body=userBody(learner);body.put("status","LOCKED");
        write(put("/api/admin/users/"+learner.getId()),login(admin),body).andExpect(status().isOk());
        mvc.perform(get("/api/auth/session").cookie(another)).andExpect(status().isUnauthorized());
        write(post("/api/auth/login"),null,Map.of("username",learner.getUsername(),"password",password)).andExpect(status().isUnauthorized());
    }
    @Test void passwordChangeRenewsCurrentCookieAndRevokesOldToken() throws Exception {
        Cookie old=login(learner);var response=write(post("/api/users/me/change-password"),old,Map.of("newPassword","New-Password-123")).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/auth/session").cookie(old)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").cookie(response.getResponse().getCookie("access_token"))).andExpect(status().isOk());
    }
    @Test void csrfAndLegacyPasswordResetAreBlocked() throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(login(learner))).andExpect(status().isForbidden());
        write(post("/api/auth/reset-password-direct"),null,Map.of("username","learner","email","x@y.vn","employeeId","NV1","newPassword",password)).andExpect(status().isUnauthorized());
        write(post("/api/auth/register"),null,Map.of()).andExpect(status().isUnauthorized());
    }
    @Test void mandatoryUnitGrantAllowsLearningButDoesNotAllowEditing() throws Exception {
        Cookie jwt=login(learner);
        mvc.perform(get("/api/lessons/LB").cookie(jwt)).andExpect(status().isForbidden());
        write(put("/api/admin/courses/CB/access"),login(admin),Map.of("ownerUnitId","B","unitGrants",Map.of("A","MANDATORY"),"instructorIds",List.of())).andExpect(status().isOk());
        mvc.perform(get("/api/lessons/LB").cookie(jwt)).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").cookie(jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.mandatoryCourses").isArray());
        write(put("/api/admin/lessons/LB"),jwt,Map.of("title","Edit")).andExpect(status().isForbidden());
        write(put("/api/admin/courses/CB/access"),login(admin),Map.of("ownerUnitId","B","unitGrants",Map.of(),"instructorIds",List.of())).andExpect(status().isOk());
        mvc.perform(get("/api/lessons/LB").cookie(jwt)).andExpect(status().isForbidden());
    }
    @Test void organizationCyclesAndForeignSharesAreRejected() throws Exception {
        write(put("/api/admin/units/A"),login(admin),Map.of("id","A","name","A","type","UNIT","parentId","A1","active",true)).andExpect(status().isBadRequest());
        write(put("/api/admin/courses/CA/access"),login(ua),Map.of("ownerUnitId","A","unitGrants",Map.of("B","OPTIONAL"),"instructorIds",List.of(instructor.getId()))).andExpect(status().isForbidden());
    }
    @Test void multiRoleUserRetainsLearnerAndInstructorPermissions() throws Exception {
        instructor.setRoles(new HashSet<>(Set.of("ROLE_USER","ROLE_INSTRUCTOR")));users.saveAndFlush(instructor);
        Cookie jwt=login(instructor);
        mvc.perform(get("/api/auth/session").cookie(jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
        mvc.perform(get("/api/admin/courses").cookie(jwt)).andExpect(status().isOk());
    }
    @Test void disabledUnitRevokesAccessAndForeignEnrollmentCannotOverrideScope() throws Exception {
        learner.getRegisteredCourses().add(cb);users.saveAndFlush(learner);Cookie jwt=login(learner);
        mvc.perform(get("/api/lessons/LB").cookie(jwt)).andExpect(status().isForbidden());
        a.setActive(false);units.saveAndFlush(a);
        mvc.perform(get("/api/auth/session").cookie(jwt)).andExpect(status().isUnauthorized());
    }
    String resetCode() throws Exception {
        write(post("/api/auth/forgot-password-otp"),null,Map.of("username",learner.getUsername(),"email",learner.getEmail())).andExpect(status().isOk());
        var captor=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(sender).send(captor.capture());
        var matcher=java.util.regex.Pattern.compile("\\d{6}").matcher(captor.getValue().getText());assertThat(matcher.find()).isTrue();return matcher.group();
    }
    @Test void otpIsHashedSingleUseAndRevokesExistingSessions() throws Exception {
        Cookie old=login(learner);String code=resetCode();
        assertThat(otps.findByUser(learner).orElseThrow().getOtpCode()).isNotEqualTo(code);
        write(post("/api/auth/reset-password-otp"),null,Map.of("username",learner.getUsername(),"otp",code,"newPassword","Reset-Password-123")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/session").cookie(old)).andExpect(status().isUnauthorized());
        write(post("/api/auth/reset-password-otp"),null,Map.of("username",learner.getUsername(),"otp",code,"newPassword","Reset-Password-456")).andExpect(status().isBadRequest());
    }
    @Test void fiveIncorrectOtpAttemptsLockTheChallenge() throws Exception {
        String code=resetCode();String wrong=code.equals("999999")?"000000":"999999";
        for(int i=0;i<5;i++) write(post("/api/auth/reset-password-otp"),null,Map.of("username",learner.getUsername(),"otp",wrong,"newPassword","Reset-Password-123")).andExpect(status().isBadRequest());
        write(post("/api/auth/reset-password-otp"),null,Map.of("username",learner.getUsername(),"otp",code,"newPassword","Reset-Password-123")).andExpect(status().isBadRequest());
    }
    @Test void firstLoginCannotBypassPasswordChangeThroughApi() throws Exception {
        learner.setMustChangePassword(true);users.saveAndFlush(learner);Cookie jwt=login(learner);
        mvc.perform(get("/api/courses").cookie(jwt)).andExpect(status().isForbidden());
        write(post("/api/users/me/change-password"),jwt,Map.of("newPassword","Changed-Password-123")).andExpect(status().isOk());
    }
    @Test void scopedGroupsAndUserCreationEnforceScope() throws Exception {
        Cookie jwt=login(ua);
        write(post("/api/admin/groups"),jwt,Map.of("id","FOREIGN","name","Foreign","unitId","B","mandatoryCourseIds",List.of())).andExpect(status().isForbidden());
        write(post("/api/admin/groups"),jwt,Map.of("id","LOCAL","name","Local","unitId","A1","mandatoryCourseIds",List.of("CA"))).andExpect(status().isOk());
        var body=userBody(learner);body.put("username","child-user");body.put("unitId","A1");body.put("password",password);body.put("userGroupId","LOCAL");
        write(post("/api/admin/users"),jwt,body).andExpect(status().isOk());
        body.put("username","foreign-user");body.put("unitId","B");write(post("/api/admin/users"),jwt,body).andExpect(status().isForbidden());
    }

    @Test void excelImportDoesNotCreatePredictablePasswords() throws Exception {
        byte[] workbook;
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var out=new java.io.ByteArrayOutputStream()) {
            var sheet=book.createSheet("Users");sheet.createRow(0);var row=sheet.createRow(1);
            row.createCell(1).setCellValue("NVIMPORT");row.createCell(2).setCellValue("import@example.vn");row.createCell(3).setCellValue("Imported user");book.write(out);workbook=out.toByteArray();
        }
        var file=new org.springframework.mock.web.MockMultipartFile("file","users.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",workbook);
        write(multipart("/api/admin/users/upload").file(file),login(ua),null).andExpect(status().isForbidden());
        write(multipart("/api/admin/users/upload").file(file),login(admin),null).andExpect(status().isOk());
        User imported=users.findByUsername("import@example.vn").orElseThrow();
        assertThat(passwords.matches("Bidv@NVIMPORT",imported.getPassword())).isFalse();assertThat(imported.isMustChangePassword()).isTrue();
    }

    @Test void navigationSeparatesAdministrativeMenusAndRespectsRoles() throws Exception {
        String response=mvc.perform(get("/api/navigation/me").cookie(login(ua))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<String> visibleRoutes=new java.util.ArrayList<>();
        for(var item:json.readTree(response)) if(item.path("visible").asBoolean()) visibleRoutes.add(item.path("route").asText());
        assertThat(visibleRoutes).contains("/admin/users","/admin/units","/admin/groups","/admin/permissions").doesNotContain("/admin/access");
        String teacher=mvc.perform(get("/api/navigation/me").cookie(login(instructor))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(teacher).doesNotContain("/admin/users","/admin/units","/admin/groups","/admin/permissions");
    }


    @Autowired AccessRoleRepository dynamicRoles;
    @Autowired com.bidv.coretraining.service.PermissionCache permissionCache;
    @Autowired com.bidv.coretraining.config.DynamicAccessInitializer migration;
    AccessRole dynamicRole(String code,Set<String> p,Set<String> scope){return dynamicRoles.save(AccessRole.builder().code(code).name(code).learningEnabled(false).permissions(new HashSet<>(p)).unitIds(new HashSet<>(scope)).build());}
    void assignDynamic(User u,AccessRole...roles){u.setDynamicAccessInitialized(true);u.setPolicySnapshot(null);u.setAssignedRoles(new HashSet<>(List.of(roles)));users.saveAndFlush(u);permissionCache.invalidate();}
    void programmeScope(){ca.getProgram().getUnits().add(a);programs.saveAndFlush(ca.getProgram());}

    @Test void jwtContainsOnlyImmutableUserIdAndSessionVersion() throws Exception {
        Cookie token=login(admin);var claims=json.readTree(Base64.getUrlDecoder().decode(token.getValue().split("\\.")[1]));
        assertThat(claims.get("sub").asText()).isEqualTo(admin.getId().toString());assertThat(claims.has("role")).isFalse();assertThat(claims.has("fullName")).isFalse();assertThat(claims.has("ver")).isTrue();
    }
    @Test void dynamicReadOnlyRoleHidesMenusAndRejectsWriteApis() throws Exception {
        programmeScope();var role=dynamicRole("CONTENT_READER",Set.of("courses:read"),Set.of("A"));assignDynamic(viewer,role);Cookie token=login(viewer);
        mvc.perform(get("/api/admin/courses").cookie(token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].allowedActions[0]").value("read"));
        var nav=mvc.perform(get("/api/navigation/me").cookie(token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(nav).contains("admin.courses").doesNotContain("admin.users","admin.permissions","admin.roles");
        write(put("/api/admin/courses/CA"),token,Map.of()).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/quizzes/questions").param("courseId","CA").cookie(token)).andExpect(status().isForbidden());
    }
    @Test void crudAndScopeArePairedPerRoleIncludingDirectObjectAccess() throws Exception {
        programmeScope();var pb=programs.save(TrainingProgram.builder().code("PB").name("PB").domain(ca.getProgram().getDomain()).units(new HashSet<>(Set.of(b))).build());cb.setProgram(pb);courses.saveAndFlush(cb);
        var writer=dynamicRole("WRITER_A",Set.of("courses:read","courses:update"),Set.of("A"));var reader=dynamicRole("READER_B",Set.of("courses:read"),Set.of("B"));assignDynamic(viewer,writer,reader);Cookie token=login(viewer);
        assertThat(AccessPolicy.allowsCourse(viewer,"courses:update",ca)).isTrue();assertThat(AccessPolicy.allowsCourse(viewer,"courses:update",cb)).isFalse();
        mvc.perform(get("/api/admin/courses").cookie(token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        write(post("/api/admin/courses/CB/status"),token,Map.of("status","DRAFT")).andExpect(status().isForbidden());
        write(delete("/api/admin/courses/CA"),token,null).andExpect(status().isForbidden());
    }
    @Test void parentScopeCoversDescendantsAndAllCoursesInProgramme() throws Exception {
        ca.getProgram().setUnits(new HashSet<>(Set.of(child)));programs.saveAndFlush(ca.getProgram());assignDynamic(viewer,dynamicRole("CHILD_READER",Set.of("courses:read"),Set.of("A")));
        mvc.perform(get("/api/admin/courses").cookie(login(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/courses/CA/outline").cookie(login(viewer))).andExpect(status().isOk());
    }
    @Test void roleCrudValidatesRegistryAndAutomaticallyIncludesReadPermission() throws Exception {
        Cookie token=login(admin);var result=write(post("/api/admin/rbac/roles"),token,Map.of("code","CUSTOM_ROLE","name","Role mới","active",true,"learningEnabled",true)).andExpect(status().isOk()).andReturn();String id=json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        write(put("/api/admin/rbac/permissions/"+id),token,Map.of("permissions",List.of("courses:update"),"unitIds",List.of("A"))).andExpect(status().isOk()).andExpect(jsonPath("$.permissions",org.hamcrest.Matchers.hasItems("courses:read","courses:update")));
        write(put("/api/admin/rbac/permissions/"+id),token,Map.of("permissions",List.of("courses:invented"),"unitIds",List.of("A"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/rbac/pages").cookie(token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Tổng quan"));
    }
    @Test void membershipsChangeExistingJwtPermissionsWithoutRelogin() throws Exception {
        programmeScope();AccessRole r=dynamicRole("REASSIGN",Set.of("courses:read"),Set.of("A"));assignDynamic(viewer);Cookie token=login(viewer);
        mvc.perform(get("/api/admin/courses").cookie(token)).andExpect(status().isForbidden());
        write(put("/api/admin/rbac/roles/"+r.getId()+"/members"),login(admin),Map.of("userIds",List.of(viewer.getId()))).andExpect(status().isOk());permissionCache.invalidate();
        mvc.perform(get("/api/admin/courses").cookie(token)).andExpect(status().isOk());
        write(put("/api/admin/rbac/roles/"+r.getId()+"/members"),login(admin),Map.of("userIds",List.of())).andExpect(status().isOk());permissionCache.invalidate();
        mvc.perform(get("/api/admin/courses").cookie(token)).andExpect(status().isForbidden());
    }
    @Test void dynamicScopeManagerCannotDelegateForeignScopeOrEscalateSelf() throws Exception {
        var manager=dynamicRole("DELEGATE_A",Set.of("permissions:read","permissions:update","roles:read","roles:update","courses:read","courses:update"),Set.of("A"));var target=dynamicRole("TARGET_A",Set.of("courses:read"),Set.of("A"));assignDynamic(ua,manager);Cookie token=login(ua);
        write(put("/api/admin/rbac/permissions/"+target.getId()),token,Map.of("permissions",List.of("courses:read"),"unitIds",List.of("B"))).andExpect(status().isForbidden());
        write(put("/api/admin/rbac/roles/"+target.getId()+"/members"),token,Map.of("userIds",List.of(ua.getId()))).andExpect(status().isForbidden());
    }
    @Test void protectedRoleAndUnknownAdminEndpointsFailClosed() throws Exception {
        var r=dynamicRoles.save(AccessRole.builder().code("PROTECTED_TEST").name("Root").protectedRole(true).build());Cookie token=login(admin);
        write(delete("/api/admin/rbac/roles/"+r.getId()),token,null).andExpect(status().isForbidden());
        write(put("/api/admin/rbac/permissions/"+r.getId()),token,Map.of("permissions",List.of(),"unitIds",List.of())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/unknown-page").cookie(token)).andExpect(status().isForbidden());
    }
    @Test void programmesCanBelongToMultipleUnitsButUnknownUnitIsRejected() throws Exception {
        Cookie token=login(admin);var body=new HashMap<String,Object>();body.put("code","MULTI");body.put("name","Multi unit programme");body.put("domainId",ca.getProgram().getDomain().getId());body.put("status","ACTIVE");body.put("unitIds",List.of("A","B"));
        write(post("/api/admin/training-programs"),token,body).andExpect(status().isOk()).andExpect(jsonPath("$.unitIds",org.hamcrest.Matchers.hasItems("A","B")));
        body.put("code","INVALID");body.put("unitIds",List.of("MISSING"));write(post("/api/admin/training-programs"),token,body).andExpect(status().isNotFound());
    }
    @Test void legacyMigrationKeepsUnitAdminsSeparateAndInstructorAssignments() {
        migration.run();assertThat(ua.isDynamicAccessInitialized()).isTrue();assertThat(ub.isDynamicAccessInitialized()).isTrue();
        assertThat(ua.getAssignedRoles().iterator().next().getId()).isNotEqualTo(ub.getAssignedRoles().iterator().next().getId());
        assertThat(AccessPolicy.allowsCourse(ua,"courses:update",ca)).isTrue();assertThat(AccessPolicy.allowsCourse(ua,"courses:update",cb)).isFalse();
        assertThat(AccessPolicy.allowsCourse(instructor,"courses:update",ca)).isTrue();assertThat(AccessPolicy.allowsCourse(instructor,"courses:update",cb)).isFalse();
    }

    @Test void protectedMembershipCannotRemoveLastActiveSystemAdministrator() throws Exception {
        var r=dynamicRoles.save(AccessRole.builder().code("ROOT_MEMBERS").name("Root members").protectedRole(true).build());Cookie token=login(admin);
        write(put("/api/admin/rbac/roles/"+r.getId()+"/members"),token,Map.of("userIds",List.of(admin.getId()))).andExpect(status().isOk());permissionCache.invalidate();
        write(put("/api/admin/rbac/roles/"+r.getId()+"/members"),token,Map.of("userIds",List.of())).andExpect(status().isBadRequest());
    }
    @Test void editingRoleRequiresManagementPermissionInThatRolesScope() throws Exception {
        var aReader=dynamicRole("A_READ_FOR_GRANT",Set.of("courses:read"),Set.of("A"));var bManager=dynamicRole("B_GRANT_MANAGER",Set.of("permissions:read","permissions:update"),Set.of("B"));var target=dynamicRole("A_TARGET_POLICY",Set.of("courses:read"),Set.of("A"));assignDynamic(ua,aReader,bManager);
        write(put("/api/admin/rbac/permissions/"+target.getId()),login(ua),Map.of("permissions",List.of("courses:read"),"unitIds",List.of("A"))).andExpect(status().isForbidden());
    }
    @Test void staleRoleVersionCannotOverwriteGrantsOrMemberships() throws Exception {
        AccessRole r=dynamicRole("VERSIONED",Set.of(),Set.of());Cookie token=login(admin);long version=r.getVersion();
        write(put("/api/admin/rbac/permissions/"+r.getId()),token,Map.of("permissions",List.of("courses:read"),"unitIds",List.of("A"),"version",version)).andExpect(status().isOk());
        write(put("/api/admin/rbac/permissions/"+r.getId()),token,Map.of("permissions",List.of(),"unitIds",List.of(),"version",version)).andExpect(status().isConflict());
    }

    @Test void learningUsesDynamicPermissionInsteadOfFixedRoleCode() throws Exception {
        programmeScope();var r=dynamicRoles.save(AccessRole.builder().code("MY_CUSTOM_LEARNER").name("Học viên tự tạo").learningEnabled(true).build());assignDynamic(viewer,r);Cookie token=login(viewer);
        write(post("/api/users/me/register-course/CA"),token,null).andExpect(status().isOk());
        mvc.perform(get("/api/admin/courses").cookie(token)).andExpect(status().isForbidden());
    }
}
