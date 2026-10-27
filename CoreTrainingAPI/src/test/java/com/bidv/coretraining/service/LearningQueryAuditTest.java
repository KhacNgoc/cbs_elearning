package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.service.impl.CourseServiceImpl;
import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cold-context regression checks for bounded queries and metadata-only reads.
 */
@DataJpaTest(showSql = false, properties = { "spring.config.location=classpath:access-test.yml",
        "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.generate_statistics=true" })
@ContextConfiguration(classes = com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({ QuizContextService.class, QuizSummaryService.class, LearningAccessService.class, LearningOutlineService.class, TrainingStructureService.class,
        LearnerWorkspaceService.class, CourseMapperImpl.class, LessonProgressService.class, CourseServiceImpl.class,
        com.bidv.coretraining.service.impl.QuizServiceImpl.class, QuizMapperImpl.class, com.fasterxml.jackson.databind.ObjectMapper.class })
class LearningQueryAuditTest {
    @Autowired QuizContextService quizContext;

    @Test
    void quizContextLoadsOnlySelectedMetadataAndPreservesSettings() throws Exception {
        var f = seed(12);
        var lesson = em.find(Lesson.class, f.lesson());
        lesson.getQuiz().setPassingScore(65);
        lesson.getQuiz().setRequiredForProgress(false);
        lesson.setTextBody("private content ".repeat(5000));
        org.mockito.Mockito.when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
        org.mockito.Mockito.when(auth.edit(f.course())).thenAnswer(i -> em.find(Course.class, f.course()));
        reset();
        var context = quizContext.context(null, f.lesson());
        assertThat(context.id()).isEqualTo(f.course());
        assertThat(context.lessons()).hasSize(1);
        assertThat(context.lessons().get(0).quizPassingScore()).isEqualTo(65);
        assertThat(context.lessons().get(0).quizRequired()).isFalse();
        assertThat(context.adminActions().get("exams")).contains("read", "update");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(context);
        int bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertThat(bytes).isLessThan(1500);
        System.out.printf("QUIZ_CONTEXT_AUDIT courseLessons=24 returnedLessons=%d bytes=%d%n", context.lessons().size(), bytes);
        assertThat(json).doesNotContain("textBody", "private content", "documents", "comments", "procedureSteps");
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        for (var type : List.of(Lesson.class, LessonContent.class, Question.class, Quiz.class, Comment.class, LessonDocument.class))
            assertThat(stats.getEntityStatistics(type.getName()).getLoadCount()).as(type.getSimpleName()).isZero();
        assertThat(quizContext.context(f.course(), null).lessons()).hasSize(24);
        org.mockito.Mockito.verify(auth, org.mockito.Mockito.atLeastOnce()).edit(f.course());
    }

    @Test
    void quizContextChecksCourseScopeAndCourseOptionsDoNotLoadLessons() {
        var f = seed(8);
        org.mockito.Mockito.when(auth.current()).thenReturn(User.builder().role("ROLE_ADMIN").build());
        org.mockito.Mockito.when(auth.editableCourses()).thenAnswer(i -> List.of(em.find(Course.class, f.course())));
        org.mockito.Mockito.when(auth.edit(f.course())).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        reset();
        var options = quizContext.courses();
        assertThat(options).hasSize(1);
        assertThat(options.get(0).lessons()).isEmpty();
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(Lesson.class.getName()).getLoadCount()).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quizContext.context(null, f.lesson()))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quizContext.context("foreign", f.lesson()))
            .isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quizContext.context(null, null))
            .isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
    }

    @Autowired QuizSummaryService summaries;

    @Test
    void examSummariesAreScopedPagedAndNeverLoadQuestionBodies() {
        var target = seed(8);
        var other = seed(2);
        var allowed = em.find(Course.class, target.course());
        org.mockito.Mockito.when(auth.editableCourses()).thenReturn(List.of(allowed));
        reset();
        var first = summaries.list(null, "", 0, 5);
        assertThat(first.content()).hasSize(5);
        assertThat(first.totalElements()).isEqualTo(16);
        assertThat(first.totalPages()).isEqualTo(4);
        assertThat(first.content()).allSatisfy(row -> {
            assertThat(row.courseId()).isEqualTo(target.course());
            assertThat(row.questionCount()).isEqualTo(1L);
            assertThat(row.passingScore()).isEqualTo(80);
        });
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        assertThat(stats.getEntityStatistics(Question.class.getName()).getLoadCount()).isZero();
        assertThat(stats.getEntityStatistics(LessonContent.class.getName()).getLoadCount()).isZero();
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        var second = summaries.list(null, "", 1, 5);
        assertThat(second.content()).extracting(com.bidv.coretraining.dto.QuizSummaryDto::lessonId)
                .doesNotContainAnyElementsOf(first.content().stream().map(com.bidv.coretraining.dto.QuizSummaryDto::lessonId).toList());
        assertThat(summaries.list(other.course(), "", 0, 10).content()).isEmpty();
        assertThat(summaries.list(null, "no match", 0, 10).totalElements()).isZero();
        assertThat(summaries.list(null, "%", 0, 10).content()).isEmpty();
        assertThat(summaries.list(target.course(), target.lesson(), 0, 10).content()).hasSize(1);
    }

    @Test
    void emptyExamScopeDoesNotQueryQuestions() {
        org.mockito.Mockito.when(auth.editableCourses()).thenReturn(List.of());
        reset();
        assertThat(summaries.list(null, "", 0, 10).content()).isEmpty();
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount()).isZero();
        org.mockito.Mockito.verify(auth).requireOperation("exams", "read");
    }

    @org.springframework.boot.test.mock.mockito.SpyBean
    com.bidv.coretraining.repository.LessonDocumentRepository documentRepository;
    @Autowired com.bidv.coretraining.service.impl.QuizServiceImpl quizService;
    @Autowired
    EntityManager em;
    @Autowired
    EntityManagerFactory emf;
    @Autowired
    TrainingStructureService structure;
    @Autowired
    LessonProgressService progress;
    @Autowired
    CourseServiceImpl service;
    @Autowired
    LearningAccessService access;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockBean
    AuthorizationService auth;
    @MockBean
    CourseImageService images;
    @MockBean
    NotificationService notifications;

    record Fixture(String course, String user, String lesson, int count) {
    }

    void reset() {
        em.flush();
        em.clear();
        emf.unwrap(SessionFactory.class).getStatistics().clear();
    }

    void report(String operation, Fixture f) {
        var s = emf.unwrap(SessionFactory.class).getStatistics();
        System.out.printf(
                "QUERY_AUDIT operation=%s lessons=%d sql=%d contents=%d quizzes=%d questions=%d comments=%d documents=%d%n",
                operation, f.count(), s.getPrepareStatementCount(),
                s.getEntityStatistics(LessonContent.class.getName()).getLoadCount(),
                s.getEntityStatistics(Quiz.class.getName()).getLoadCount(),
                s.getEntityStatistics(Question.class.getName()).getLoadCount(),
                s.getEntityStatistics(Comment.class.getName()).getLoadCount(),
                s.getEntityStatistics(LessonDocument.class.getName()).getLoadCount());
    }

    Fixture seed(int perChapter) {
        String id = UUID.randomUUID().toString();
        var unit = OrganizationUnit.builder().id(id).name("Unit").build();
        em.persist(unit);
        var domain = TrainingDomain.builder().code(id).name("Domain").build();
        em.persist(domain);
        var program = TrainingProgram.builder().code(id).name("Program").domain(domain).build();
        em.persist(program);
        var course = Course.builder().id(id).code(id).name("Course").ownerUnit(unit).program(program)
                .status("PUBLISHED").build();
        em.persist(course);
        String first = null;
        for (int ch = 0; ch < 2; ch++) {
            var chapter = Chapter.builder().course(course).code("CH" + ch).title("Chapter " + ch).chapterOrder(ch)
                    .status("PUBLISHED").build();
            em.persist(chapter);
            course.getChapters().add(chapter);
            for (int i = 0; i < perChapter; i++) {
                var lesson = Lesson.builder().id(id + "-" + ch + "-" + i).title("Lesson").course(course)
                        .chapter(chapter).lessonOrder(i).status("PUBLISHED").build();
                if (first == null)
                    first = lesson.getId();
                lesson.ensureContent();
                lesson.setTextBody("Long content ".repeat(200));
                lesson.ensureQuiz();
                lesson.getQuiz().getQuestions()
                        .add(Question.builder().course(course).lesson(lesson).quiz(lesson.getQuiz()).text("Question")
                                .correctAnswer(0).options(new ArrayList<>(List.of("A", "B"))).build());
                lesson.getDocuments().add(LessonDocument.builder().lesson(lesson).name("Doc")
                        .documentType(DocumentType.PDF).fileUrl("/uploads/a.pdf").documentOrder(0).build());
                lesson.getComments().add(Comment.builder().lesson(lesson).username("someone").avatar("S")
                        .content("Comment").createdAt(LocalDateTime.now()).build());
                em.persist(lesson);
                chapter.getLessons().add(lesson);
                course.getLessons().add(lesson);
            }
        }
        var user = User.builder().username(id).password("test").fullName("Learner").role("ROLE_USER")
                .organizationUnit(unit).registeredCourses(new HashSet<>(Set.of(course))).build();
        em.persist(user);
        return new Fixture(id, id, first, perChapter * 2);
    }

    @Test
    void resumeDoesNotLoadProgressFromOtherCourses() {
        var target = seed(2);
        var other = seed(6);
        var user = em.createQuery("select u from User u where u.username=:username", User.class)
                .setParameter("username", target.user()).getSingleResult();
        var unrelated = em.createQuery("select l from Lesson l where l.course.id=:course", Lesson.class)
                .setParameter("course", other.course()).getResultList();
        for (var lesson : unrelated)
            em.persist(LessonProgress.builder().user(user).lesson(lesson).updatedAt(LocalDateTime.now()).build());
        reset();
        var dto = structure.outline(target.course(), target.user());
        assertThat(dto.chapters()).hasSize(2);
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(LessonProgress.class.getName())
                .getLoadCount()).isZero();
        System.out.printf("QUERY_AUDIT_HISTORY targetLessons=%d unrelatedProgress=%d loadedProgress=%d%n",
                target.count(), unrelated.size(),
                emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(LessonProgress.class.getName())
                        .getLoadCount());
    }

    @Test
    void documentPositionsSurviveSwitchingDocumentsAndPrimaryContent() {
        var f = seed(2);
        var lesson = em.find(Lesson.class, f.lesson());
        var first = lesson.getDocuments().get(0);
        var second = LessonDocument.builder().lesson(lesson).name("Second").documentType(DocumentType.PDF)
                .fileUrl("/uploads/second.pdf").documentOrder(1).build();
        em.persist(second);
        em.flush();
        var firstId = first.getId();
        var secondId = second.getId();
        reset();
        var request = new LessonProgressRequest();
        request.setContentType("DOCUMENT");
        request.setDocumentId(firstId);
        request.setDocumentPage(7);
        request.setScrollOffset(100D);
        progress.saveProgress(f.user(), f.lesson(), request);
        reset();
        // Simulate a legacy row with only documentId/documentPage populated.
        em.createQuery("update LessonProgress p set p.documentPositions = null where p.lesson.id = :lesson")
                .setParameter("lesson", f.lesson()).executeUpdate();
        reset();
        request.setDocumentId(secondId);
        request.setDocumentPage(3);
        request.setScrollOffset(20D);
        var saved = progress.saveProgress(f.user(), f.lesson(), request);
        assertThat(saved.getDocumentPositions().get(firstId.toString()).page()).isEqualTo(7);
        reset();
        request.setContentType("TEXT");
        progress.saveProgress(f.user(), f.lesson(), request);
        reset();
        var user = em.createQuery("select u from User u where u.username=:name", User.class)
                .setParameter("name", f.user()).getSingleResult();
        var restored = progress.getAllProgress(user).get(f.lesson());
        assertThat(restored.getDocumentPositions().get(firstId.toString()).page()).isEqualTo(7);
        assertThat(restored.getDocumentPositions().get(secondId.toString()).page()).isEqualTo(3);
        assertThat(restored.getDocumentPositions().get(firstId.toString()).scrollOffset()).isEqualTo(100D);
    }

    @Test
    void measureTwoCourseSizes() {
        Map<String, Long> counts = new HashMap<>();
        for (int size : List.of(2, 6)) {
            var f = seed(size);
            reset();
            var dto = structure.outline(f.course(), f.user());
            assertThat(dto.chapters().stream().mapToInt(c -> c.lessons().size()).sum()).isEqualTo(f.count());
            assertThat(dto.chapters().get(0).lessons().get(0).getTextBody()).isNull();
            assertThat(dto.chapters().stream().flatMap(c -> c.lessons().stream()))
                    .allSatisfy(lesson -> {
                        assertThat(lesson.getDocuments()).hasSize(1);
                        assertThat(lesson.getDocuments().get(0).getName()).isEqualTo("Doc");
                        assertThat(lesson.getDocuments().get(0).getDocumentType()).isEqualTo(DocumentType.PDF);
                        assertThat(lesson.getDocuments().get(0).getFileUrl()).isEqualTo("/uploads/a.pdf");
                    });
            assertThat(dto.chapters().get(0).lessons().get(0).getComments()).isEmpty();
            report("outline", f);
            assertMetadataOnly();
            assertThat(dto.course().getLessons()).isEmpty();
            assertBounded(counts, "outline");
            System.out.printf("QUERY_AUDIT_PAYLOAD chapterLessons=%d duplicateCourseLessons=%d%n", f.count(),
                    dto.course().getLessons().size());
            reset();
            var request = new LessonProgressRequest();
            request.setContentType("TEXT");
            progress.saveProgress(f.user(), f.lesson(), request);
            em.flush();
            report("save-progress", f);
            assertMetadataOnly();
            assertBounded(counts, "progress");
            reset();
            org.mockito.Mockito.when(auth.current())
                    .thenReturn(em.createQuery("select u from User u where u.username=:name", User.class)
                            .setParameter("name", f.user()).getSingleResult());
            assertThat(service.getLessonById(f.lesson()).getTextBody()).contains("Long content");
            report("lesson-with-gate", f);
            assertThat(emf.unwrap(SessionFactory.class).getStatistics()
                    .getEntityStatistics(LessonContent.class.getName()).getLoadCount()).isEqualTo(1);
            assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(Question.class.getName())
                    .getLoadCount()).isZero();
            assertBounded(counts, "lesson");
            reset();
            assertThat(service.getVideoNotes(f.lesson(), f.user())).isEmpty();
            report("notes-empty", f);
            assertMetadataOnly();
            assertBounded(counts, "notes");
        }
    }

    @Test void automaticCompletionRequiresTextVideoAndEveryRequiredSlide() {
        var f = seed(2);
        var lesson = em.find(Lesson.class, f.lesson());
        lesson.getQuiz().setRequiredForProgress(false);
        lesson.setVideoUrl("/video.mp4");
        var first = lesson.getDocuments().get(0).getId();
        lesson.getDocuments().get(0).setPurpose(DocumentPurpose.SLIDE);
        lesson.getDocuments().get(0).setDocumentType(DocumentType.POWERPOINT);
        lesson.getDocuments().get(0).setFileUrl("/uploads/documents/first.pptx");
        var other = LessonDocument.builder().lesson(lesson).name("Other").documentType(DocumentType.POWERPOINT)
            .purpose(DocumentPurpose.SLIDE).fileUrl("/uploads/documents/other.pptx").documentOrder(2).build();
        em.persist(other); em.flush(); var second = other.getId(); reset();
        var request = new LessonProgressRequest(); request.setContentType("VIDEO");
        request.setDurationSeconds(100D); request.setWatchedSeconds(90D);
        assertThat(progress.saveProgress(f.user(), f.lesson(), request).isLessonCompleted()).isFalse();
        request.setContentType("TEXT"); request.setTextCompleted(true);
        assertThat(progress.saveProgress(f.user(), f.lesson(), request).isLessonCompleted()).isFalse();
        request.setContentType("DOCUMENT"); request.setDocumentId(first); request.setDocumentPage(8); request.setDocumentCompleted(true);
        assertThat(progress.saveProgress(f.user(), f.lesson(), request).isLessonCompleted()).isFalse();
        request.setDocumentId(second);
        assertThat(progress.saveProgress(f.user(), f.lesson(), request).isLessonCompleted()).isTrue();
        reset(); request.setDocumentPage(1); request.setDocumentCompleted(false);
        assertThat(progress.saveProgress(f.user(), f.lesson(), request).isLessonCompleted()).isTrue();
        var user = em.createQuery("select u from User u where u.username=:name", User.class).setParameter("name", f.user()).getSingleResult();
        assertThat(user.getPoints()).isEqualTo(100);
    }

    @Test void bookmarkOnlyUpdatesSkipCompletionQueriesAndDuplicateWrites() {
        var f = seed(2); reset();
        var request = new LessonProgressRequest(); request.setContentType("VIDEO");
        request.setDurationSeconds(100D); request.setWatchedSeconds(10D); request.setLastPositionSeconds(10D);
        var initial = progress.saveProgress(f.user(), f.lesson(), request); reset();
        org.mockito.Mockito.clearInvocations(documentRepository);
        var duplicate = progress.saveProgress(f.user(), f.lesson(), request); em.flush();
        assertThat(duplicate.getUpdatedAt()).isCloseTo(initial.getUpdatedAt(),
            org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityUpdateCount()).isZero();
        report("bookmark-duplicate", f);
        reset();
        request.setWatchedSeconds(20D); request.setLastPositionSeconds(20D);
        progress.saveProgress(f.user(), f.lesson(), request); em.flush();
        report("bookmark-hot", f);
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.never()).findAllByLessonIdAndPurpose(f.lesson(), DocumentPurpose.SLIDE);
        request.setWatchedSeconds(90D);
        progress.saveProgress(f.user(), f.lesson(), request); em.flush();
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.never()).findAllByLessonIdAndPurpose(f.lesson(), DocumentPurpose.SLIDE);
        request.setWatchedSeconds(95D);
        progress.saveProgress(f.user(), f.lesson(), request); em.flush();
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.never()).findAllByLessonIdAndPurpose(f.lesson(), DocumentPurpose.SLIDE);
        request.setContentType("TEXT"); request.setTextCompleted(true);
        progress.saveProgress(f.user(), f.lesson(), request); em.flush();
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.times(1)).findAllByLessonIdAndPurpose(f.lesson(), DocumentPurpose.SLIDE);
        // Editing metadata invalidates the last evaluation for an unfinished lesson.
        em.find(Lesson.class, f.lesson()).setTitle("Changed"); reset();
        progress.saveProgress(f.user(), f.lesson(), request); em.flush();
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.times(2)).findAllByLessonIdAndPurpose(f.lesson(), DocumentPurpose.SLIDE);
    }

    @Test void quizPassBeforeContentDoesNotCompleteLessonUntilContentIsFinished() {
        for (boolean required : List.of(true, false)) {
            var f = seed(2); var lesson = em.find(Lesson.class, f.lesson());
            lesson.getQuiz().setRequiredForProgress(required);
            var question = lesson.getQuiz().getQuestions().get(0);
            em.flush(); var questionId = question.getId(); reset();
            var request = new QuizSubmitRequest(); request.setSelectedAnswers(Map.of(questionId, List.of(0)));
            assertThat(quizService.submitLessonQuiz(f.lesson(), request, f.user()).getPassed()).isTrue();
            reset();
            var user = em.createQuery("select u from User u where u.username=:name", User.class).setParameter("name", f.user()).getSingleResult();
            assertThat(user.getCompletedLessons().stream().anyMatch(l -> l.getId().equals(f.lesson()))).isFalse();
            var content = new LessonProgressRequest(); content.setContentType("TEXT"); content.setTextCompleted(true);
            assertThat(progress.saveProgress(f.user(), f.lesson(), content).isLessonCompleted()).isTrue();
        }
    }

    @Test void requiredQuizPassAfterContentAwardsCompletionOnce() {
        var f = seed(2);
        var content = new LessonProgressRequest(); content.setContentType("TEXT"); content.setTextCompleted(true);
        assertThat(progress.saveProgress(f.user(), f.lesson(), content).isLessonCompleted()).isFalse();
        var lesson = em.find(Lesson.class, f.lesson());
        var request = new QuizSubmitRequest();
        request.setSelectedAnswers(Map.of(lesson.getQuiz().getQuestions().get(0).getId(), List.of(0)));
        quizService.submitLessonQuiz(f.lesson(), request, f.user());
        quizService.submitLessonQuiz(f.lesson(), request, f.user());
        var user = em.createQuery("select u from User u where u.username=:name", User.class)
                .setParameter("name", f.user()).getSingleResult();
        assertThat(user.getCompletedLessons()).extracting(Lesson::getId).contains(f.lesson());
        assertThat(user.getPoints()).isEqualTo(100);
    }

    @Test void supportingOnlyLessonCannotBePublishedButSlideOnlyLessonCan() {
        var f = seed(2);
        var lesson = em.find(Lesson.class, f.lesson());
        lesson.setTextBody(null);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> structure.status(f.course(), "PUBLISHED")))
                .isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        var slide = lesson.getDocuments().get(0);
        slide.setPurpose(DocumentPurpose.SLIDE); slide.setDocumentType(DocumentType.POWERPOINT);
        slide.setFileUrl("/uploads/documents/slide.pptx");
        assertThat(structure.status(f.course(), "PUBLISHED").getStatus()).isEqualTo("PUBLISHED");
    }

    @Test void allProgressReevaluatesCurrentRequirementsInBoundedMetadataQueries() {
        for (int size : List.of(2, 6)) {
            var f = seed(size);
            var user = em.createQuery("select u from User u where u.username=:name", User.class)
                    .setParameter("name", f.user()).getSingleResult();
            for (var lesson : em.createQuery("select l from Lesson l where l.course.id=:course", Lesson.class)
                    .setParameter("course", f.course()).getResultList())
                em.persist(LessonProgress.builder().lesson(lesson).user(user).textCompleted(true)
                        .contentCompleted(false).updatedAt(LocalDateTime.now()).build());
            reset();
            var result = progress.getAllProgress(user);
            assertThat(result).hasSize(f.count());
            assertThat(result.values()).allSatisfy(p -> assertThat(p.isContentCompleted()).isTrue());
            assertMetadataOnly();
            assertThat(emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(3);
        }
    }

    void assertMetadataOnly() {
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        for (Class<?> type : List.of(LessonContent.class, Quiz.class, Question.class, Comment.class,
                LessonDocument.class))
            assertThat(statistics.getEntityStatistics(type.getName()).getLoadCount()).as(type.getSimpleName()).isZero();
    }

    void assertBounded(Map<String, Long> counts, String operation) {
        long count = emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
        Long previous = counts.putIfAbsent(operation, count);
        assertThat(count).as(operation + " SQL bound").isLessThanOrEqualTo(18);
        if (previous != null)
            assertThat(count).as(operation + " must not grow with sibling count").isEqualTo(previous);
    }

    @Test
    void gatesAndResumePreservePublishedSequentialLearning() {
        var f = seed(2);
        reset();
        var outline = structure.outline(f.course(), f.user());
        var first = outline.chapters().get(0).lessons().get(0).getId();
        var second = outline.chapters().get(0).lessons().get(1).getId();
        var nextChapter = outline.chapters().get(1).lessons().get(0).getId();
        assertThat(outline.chapters().get(1).locked()).isTrue();
        assertThat(access.requireLesson(f.user(), first).lesson().getId()).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> access.requireLesson(f.user(), second))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var user = em.createQuery("select u from User u where u.username=:name", User.class)
                .setParameter("name", f.user()).getSingleResult();
        user.getCompletedLessons().add(em.find(Lesson.class, first));
        reset();
        assertThat(access.requireLesson(f.user(), second).lesson().getId()).isEqualTo(second);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> access.requireLesson(f.user(), nextChapter))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        em.persist(LessonProgress.builder().user(em.find(User.class, user.getId()))
                .lesson(em.getReference(Lesson.class, second)).contentType("TEXT").textCompleted(true).contentCompleted(true)
                .updatedAt(LocalDateTime.now()).build());
        reset();
        assertThat(structure.outline(f.course(), f.user()).resume().contentType()).isEqualTo("QUIZ");
        var secondLesson = em.find(Lesson.class, second);
        secondLesson.getQuiz().getQuestions().clear();
        reset();
        assertThat(structure.outline(f.course(), f.user()).resume().contentType()).isEqualTo("TEXT");
        secondLesson = em.find(Lesson.class, second);
        secondLesson.setStatus("DRAFT");
        reset();
        assertThat(structure.outline(f.course(), f.user()).chapters().get(0).lessons()).hasSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> access.requireLesson(f.user(), second))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(access.requireLesson(f.user(), nextChapter).lesson().getId()).isEqualTo(nextChapter);
    }

    @Test
    void commentsArePagedAndRepliesAreLoadedInOneBatch() {
        var f = seed(2);
        var lesson = em.find(Lesson.class, f.lesson());
        for (int i = 0; i < 25; i++) {
            var root = Comment.builder().lesson(lesson).username("root").avatar("R").content("Root " + i)
                    .createdAt(LocalDateTime.now().plusSeconds(i)).build();
            em.persist(root);
            em.persist(Comment.builder().lesson(lesson).parent(root).username("reply").avatar("R")
                    .content("Reply").createdAt(LocalDateTime.now()).build());
        }
        reset();
        org.mockito.Mockito.when(auth.current())
                .thenReturn(em.createQuery("select u from User u where u.username=:name", User.class)
                        .setParameter("name", f.user()).getSingleResult());
        var detail = service.getLessonById(f.lesson());
        assertThat(detail.getCommentCount()).isEqualTo(26);
        assertThat(detail.getComments()).hasSize(20);
        assertThat(detail.getComments()).allSatisfy(c -> assertThat(c.getReplies()).hasSize(1));
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(18);
        var remaining = service.getComments(f.lesson(), 1, 20);
        assertThat(remaining.content()).hasSize(6);
        assertThat(remaining.last()).isTrue();
        assertThat(remaining.content()).extracting(CommentDto::getId)
                .doesNotContainAnyElementsOf(detail.getComments().stream().map(CommentDto::getId).toList());
    }

    @Test void initialCommentPageLoadsOnlyFiveRepliesPerThread() {
        var f = seed(2);
        var lesson = em.find(Lesson.class, f.lesson());
        var root = Comment.builder().lesson(lesson).username("root").avatar("R").content("Thread")
                .createdAt(LocalDateTime.now()).build();
        em.persist(root);
        for (int i = 0; i < 12; i++)
            em.persist(Comment.builder().lesson(lesson).parent(root).username("reply").avatar("R")
                    .content("Reply " + i).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());
        em.flush(); var rootId = root.getId(); reset();
        org.mockito.Mockito.when(auth.current()).thenReturn(em.createQuery("select u from User u where u.username=:name", User.class)
                .setParameter("name", f.user()).getSingleResult());
        var page = service.getComments(f.lesson(), 0, 20);
        var thread = page.content().stream().filter(c -> c.getId().equals(rootId)).findFirst().orElseThrow();
        assertThat(thread.getReplies()).hasSize(5).extracting(CommentDto::getContent)
                .containsExactly("Reply 0", "Reply 1", "Reply 2", "Reply 3", "Reply 4");
        assertThat(thread.getRepliesHasMore()).isTrue();
        assertThat(thread.getReplyCursor()).isNotBlank();
        assertThat(thread.getReplies()).allSatisfy(reply -> assertThat(reply.getCreatedAt()).isNotBlank());
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(Comment.class.getName()).getLoadCount())
                .isLessThanOrEqualTo(8);
    }

    private Comment replyThread(String lessonId, int count) {
        var lesson = em.find(Lesson.class, lessonId);
        var root = Comment.builder().lesson(lesson).username("root").avatar("R").content("Thread")
                .createdAt(LocalDateTime.now().plusSeconds(1)).build();
        em.persist(root);
        for (int i = 0; i < count; i++)
            em.persist(Comment.builder().lesson(lesson).parent(root).username("reply").avatar("R")
                    .content("Reply " + i).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());
        em.flush();
        return root;
    }

    private void commentActor(Fixture f) {
        org.mockito.Mockito.when(auth.current()).thenReturn(em.createQuery("select u from User u where u.username=:name", User.class)
                .setParameter("name", f.user()).getSingleResult());
    }

    @Test void repliesUseFiveItemKeysetPagesAndSurviveCursorDeletionAndNewReplies() {
        var f = seed(2); var root = replyThread(f.lesson(), 12); commentActor(f);
        var first = service.getReplies(f.lesson(), root.getId(), null);
        assertThat(first.content()).hasSize(5).extracting(CommentDto::getContent)
                .containsExactly("Reply 0", "Reply 1", "Reply 2", "Reply 3", "Reply 4");
        assertThat(first.hasMore()).isTrue();
        // The cursor is a value, not a reference to a row which may be deleted.
        em.remove(em.find(Comment.class, first.content().get(4).getId())); em.flush();
        var second = service.getReplies(f.lesson(), root.getId(), first.nextCursor());
        assertThat(second.content()).hasSize(5).extracting(CommentDto::getContent)
                .containsExactly("Reply 5", "Reply 6", "Reply 7", "Reply 8", "Reply 9");
        assertThat(second.hasMore()).isTrue();
        em.persist(Comment.builder().lesson(root.getLesson()).parent(root).username("late").avatar("L")
                .content("New reply").createdAt(LocalDateTime.of(2026, 10, 1, 9, 1)).build()); em.flush();
        var last = service.getReplies(f.lesson(), root.getId(), second.nextCursor());
        assertThat(last.content()).extracting(CommentDto::getContent).containsExactly("Reply 10", "Reply 11", "New reply");
        assertThat(last.hasMore()).isFalse();
        assertThat(last.nextCursor()).isNull();
        assertThat(java.util.stream.Stream.of(first.content(), second.content(), last.content()).flatMap(List::stream)
                .map(CommentDto::getId).toList()).doesNotHaveDuplicates();
    }

    @Test void replyPaginationRejectsMalformedCursorsAndForeignOrNestedRoots() {
        var f = seed(2); var root = replyThread(f.lesson(), 1);
        var other = seed(2); var foreign = replyThread(other.lesson(), 1); commentActor(f);
        for (String cursor : List.of("", "!", "A".repeat(257), java.util.Base64.getUrlEncoder()
                .encodeToString("2026-10-01T09:00|-1".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getReplies(f.lesson(), root.getId(), cursor))
                    .isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getReplies(f.lesson(), foreign.getId(), null))
                .isInstanceOf(com.bidv.coretraining.exception.ResourceNotFoundException.class);
        var reply = em.createQuery("select c from Comment c where c.parent.id=:id", Comment.class)
                .setParameter("id", root.getId()).getSingleResult();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getReplies(f.lesson(), reply.getId(), null))
                .isInstanceOf(com.bidv.coretraining.exception.ResourceNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getReplies(other.lesson(), foreign.getId(), null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void emptyAndExactlyFiveReplyThreadsHaveNoMorePages() {
        var f = seed(2); var empty = replyThread(f.lesson(), 0); var full = replyThread(f.lesson(), 5); commentActor(f);
        var noReplies = service.getReplies(f.lesson(), empty.getId(), null);
        assertThat(noReplies.content()).isEmpty(); assertThat(noReplies.hasMore()).isFalse(); assertThat(noReplies.nextCursor()).isNull();
        var five = service.getReplies(f.lesson(), full.getId(), null);
        assertThat(five.content()).hasSize(5); assertThat(five.hasMore()).isFalse(); assertThat(five.nextCursor()).isNull();
        assertThat(service.getComments(f.lesson(), 0, 20).content()).allSatisfy(thread -> {
            assertThat(thread.getRepliesHasMore()).isFalse(); assertThat(thread.getReplyCursor()).isNull();
        });
    }

    @Test
    void learnerCatalogUsesPublishedMetadataWithoutHydratingBodies() {
        var f = seed(2);
        var draft = em.find(Lesson.class, f.course() + "-0-1");
        draft.setStatus("DRAFT");
        reset();
        org.mockito.Mockito.when(auth.current())
                .thenReturn(em.createQuery("select u from User u where u.username=:name", User.class)
                        .setParameter("name", f.user()).getSingleResult());
        var catalog = service.getAllCourses();
        var course = catalog.stream().filter(c -> c.getId().equals(f.course())).findFirst().orElseThrow();
        assertThat(course.getLessons()).hasSize(3).allSatisfy(l -> assertThat(l.getTextBody()).isNull());
        assertMetadataOnly();
        reset();
        assertThat(service.getCourseById(f.course()).getLessons()).hasSize(3);
        assertMetadataOnly();
    }

    @Test
    void emptyPublishedChapterStillBlocksLaterChapters() {
        var f = seed(2);
        em.find(Lesson.class, f.course() + "-0-0").setStatus("DRAFT");
        em.find(Lesson.class, f.course() + "-0-1").setStatus("DRAFT");
        reset();
        var outline = structure.outline(f.course(), f.user());
        assertThat(outline.chapters().get(0).lessons()).isEmpty();
        assertThat(outline.chapters().get(1).locked()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> access.requireLesson(f.user(), f.course() + "-1-0"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentFirstSavesCreateOneRowAndKeepMonotonicProgress() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        Fixture f = tx.execute(status -> seed(2));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for (double watched : List.of(10D, 100D))
                jobs.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    var request = new LessonProgressRequest();
                    request.setWatchedSeconds(watched);
                    request.setDurationSeconds(100D);
                    request.setContentType("VIDEO");
                    progress.saveProgress(f.user(), f.lesson(), request);
                }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var job : jobs)
                job.get(15, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                var saved = em.createQuery("select p from LessonProgress p where p.lesson.id=:id", LessonProgress.class)
                        .setParameter("id", f.lesson()).getResultList();
                assertThat(saved).hasSize(1);
                assertThat(saved.get(0).getWatchedSeconds()).isEqualTo(100D);
                assertThat(saved.get(0).isContentCompleted()).isFalse(); // Text and documents are still unread.
            });
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

}
