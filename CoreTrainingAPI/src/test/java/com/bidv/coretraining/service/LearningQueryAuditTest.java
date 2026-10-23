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

/** Cold-context regression checks for bounded queries and metadata-only reads. */
@DataJpaTest(showSql=false, properties={"spring.config.location=classpath:access-test.yml", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ContextConfiguration(classes=com.bidv.coretraining.CoreTrainingApiApplication.class)
@Import({LearningAccessService.class,LearningOutlineService.class,TrainingStructureService.class,LearnerWorkspaceService.class,CourseMapperImpl.class,LessonProgressService.class,CourseServiceImpl.class})
class LearningQueryAuditTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired TrainingStructureService structure;
    @Autowired LessonProgressService progress;
    @Autowired CourseServiceImpl service;
    @Autowired LearningAccessService access;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockBean AuthorizationService auth;
    @MockBean CourseImageService images;
    @MockBean NotificationService notifications;
    record Fixture(String course,String user,String lesson,int count) {}
    void reset(){em.flush();em.clear();emf.unwrap(SessionFactory.class).getStatistics().clear();}
    void report(String operation,Fixture f){
        var s=emf.unwrap(SessionFactory.class).getStatistics();
        System.out.printf("QUERY_AUDIT operation=%s lessons=%d sql=%d contents=%d quizzes=%d questions=%d comments=%d documents=%d%n",operation,f.count(),s.getPrepareStatementCount(),
            s.getEntityStatistics(LessonContent.class.getName()).getLoadCount(),s.getEntityStatistics(Quiz.class.getName()).getLoadCount(),
            s.getEntityStatistics(Question.class.getName()).getLoadCount(),s.getEntityStatistics(Comment.class.getName()).getLoadCount(),s.getEntityStatistics(LessonDocument.class.getName()).getLoadCount());
    }
    Fixture seed(int perChapter){
        String id=UUID.randomUUID().toString();
        var unit=OrganizationUnit.builder().id(id).name("Unit").build();em.persist(unit);
        var domain=TrainingDomain.builder().code(id).name("Domain").build();em.persist(domain);
        var program=TrainingProgram.builder().code(id).name("Program").domain(domain).build();em.persist(program);
        var course=Course.builder().id(id).code(id).name("Course").ownerUnit(unit).program(program).status("PUBLISHED").build();em.persist(course);
        String first=null;
        for(int ch=0;ch<2;ch++){
            var chapter=Chapter.builder().course(course).code("CH"+ch).title("Chapter "+ch).chapterOrder(ch).status("PUBLISHED").build();em.persist(chapter);course.getChapters().add(chapter);
            for(int i=0;i<perChapter;i++){
                var lesson=Lesson.builder().id(id+"-"+ch+"-"+i).title("Lesson").course(course).chapter(chapter).lessonOrder(i).status("PUBLISHED").build();
                if(first==null)first=lesson.getId();
                lesson.ensureContent();lesson.setTextBody("Long content ".repeat(200));lesson.ensureQuiz();
                lesson.getQuiz().getQuestions().add(Question.builder().course(course).lesson(lesson).quiz(lesson.getQuiz()).text("Question").correctAnswer(0).options(new ArrayList<>(List.of("A","B"))).build());
                lesson.getDocuments().add(LessonDocument.builder().lesson(lesson).name("Doc").documentType(DocumentType.PDF).fileUrl("/uploads/a.pdf").documentOrder(0).build());
                lesson.getComments().add(Comment.builder().lesson(lesson).username("someone").avatar("S").content("Comment").createdAt(LocalDateTime.now()).build());
                em.persist(lesson);chapter.getLessons().add(lesson);course.getLessons().add(lesson);
            }
        }
        var user=User.builder().username(id).password("test").fullName("Learner").role("ROLE_USER").organizationUnit(unit).registeredCourses(new HashSet<>(Set.of(course))).build();em.persist(user);
        return new Fixture(id,id,first,perChapter*2);
    }
    @Test void resumeDoesNotLoadProgressFromOtherCourses(){
        var target=seed(2);var other=seed(6);
        var user=em.createQuery("select u from User u where u.username=:username",User.class).setParameter("username",target.user()).getSingleResult();
        var unrelated=em.createQuery("select l from Lesson l where l.course.id=:course",Lesson.class).setParameter("course",other.course()).getResultList();
        for(var lesson:unrelated)em.persist(LessonProgress.builder().user(user).lesson(lesson).updatedAt(LocalDateTime.now()).build());
        reset();
        var dto=structure.outline(target.course(),target.user());
        assertThat(dto.chapters()).hasSize(2);
        assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(LessonProgress.class.getName()).getLoadCount()).isZero();
        System.out.printf("QUERY_AUDIT_HISTORY targetLessons=%d unrelatedProgress=%d loadedProgress=%d%n",target.count(),unrelated.size(),
            emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(LessonProgress.class.getName()).getLoadCount());
    }
    @Test void measureTwoCourseSizes(){
        Map<String, Long> counts = new HashMap<>();
        for(int size:List.of(2,6)){
            var f=seed(size);reset();
            var dto=structure.outline(f.course(),f.user());
            assertThat(dto.chapters().stream().mapToInt(c->c.lessons().size()).sum()).isEqualTo(f.count());
            assertThat(dto.chapters().get(0).lessons().get(0).getTextBody()).isNull();
            assertThat(dto.chapters().get(0).lessons().get(0).getComments()).isEmpty();report("outline",f);
            assertMetadataOnly();
            assertThat(dto.course().getLessons()).isEmpty();
            assertBounded(counts, "outline");
            System.out.printf("QUERY_AUDIT_PAYLOAD chapterLessons=%d duplicateCourseLessons=%d%n",f.count(),dto.course().getLessons().size());
            reset();var request=new LessonProgressRequest();request.setContentType("TEXT");
            progress.saveProgress(f.user(),f.lesson(),request);em.flush();report("save-progress",f);
            assertMetadataOnly();
            assertBounded(counts, "progress");
            reset();org.mockito.Mockito.when(auth.current()).thenReturn(em.createQuery("select u from User u where u.username=:name",User.class).setParameter("name",f.user()).getSingleResult());
            assertThat(service.getLessonById(f.lesson()).getTextBody()).contains("Long content");report("lesson-with-gate",f);
            assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(LessonContent.class.getName()).getLoadCount()).isEqualTo(1);
            assertThat(emf.unwrap(SessionFactory.class).getStatistics().getEntityStatistics(Question.class.getName()).getLoadCount()).isZero();
            assertBounded(counts, "lesson");
            reset();assertThat(service.getVideoNotes(f.lesson(),f.user())).isEmpty();report("notes-empty",f);
            assertMetadataOnly();
            assertBounded(counts, "notes");
        }
    }
    void assertMetadataOnly() {
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        for (Class<?> type : List.of(LessonContent.class, Quiz.class, Question.class, Comment.class, LessonDocument.class))
            assertThat(statistics.getEntityStatistics(type.getName()).getLoadCount()).as(type.getSimpleName()).isZero();
    }

    void assertBounded(Map<String, Long> counts, String operation) {
        long count = emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
        Long previous = counts.putIfAbsent(operation, count);
        assertThat(count).as(operation + " SQL bound").isLessThanOrEqualTo(18);
        if (previous != null) assertThat(count).as(operation + " must not grow with sibling count").isEqualTo(previous);
    }

    @Test void gatesAndResumePreservePublishedSequentialLearning() {
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
        var user = em.createQuery("select u from User u where u.username=:name", User.class).setParameter("name",f.user()).getSingleResult();
        user.getCompletedLessons().add(em.find(Lesson.class, first));
        reset();
        assertThat(access.requireLesson(f.user(), second).lesson().getId()).isEqualTo(second);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> access.requireLesson(f.user(), nextChapter))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        em.persist(LessonProgress.builder().user(em.find(User.class, user.getId()))
            .lesson(em.getReference(Lesson.class, second)).contentType("TEXT").contentCompleted(true)
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

    @Test void commentsArePagedAndRepliesAreLoadedInOneBatch() {
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
        org.mockito.Mockito.when(auth.current()).thenReturn(em.createQuery("select u from User u where u.username=:name", User.class).setParameter("name", f.user()).getSingleResult());
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

    @Test void learnerCatalogUsesPublishedMetadataWithoutHydratingBodies() {
        var f = seed(2);
        var draft = em.find(Lesson.class, f.course() + "-0-1");
        draft.setStatus("DRAFT");
        reset();
        org.mockito.Mockito.when(auth.current()).thenReturn(em.createQuery("select u from User u where u.username=:name", User.class).setParameter("name", f.user()).getSingleResult());
        var catalog = service.getAllCourses();
        var course = catalog.stream().filter(c -> c.getId().equals(f.course())).findFirst().orElseThrow();
        assertThat(course.getLessons()).hasSize(3).allSatisfy(l -> assertThat(l.getTextBody()).isNull());
        assertMetadataOnly();
        reset();
        assertThat(service.getCourseById(f.course()).getLessons()).hasSize(3);
        assertMetadataOnly();
    }

    @Test void emptyPublishedChapterStillBlocksLaterChapters() {
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
            for (double watched : List.of(10D, 100D)) jobs.add(executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                var request = new LessonProgressRequest();
                request.setWatchedSeconds(watched); request.setDurationSeconds(100D); request.setContentType("VIDEO");
                progress.saveProgress(f.user(), f.lesson(), request);
            }));
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var job : jobs) job.get(15, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                var saved = em.createQuery("select p from LessonProgress p where p.lesson.id=:id", LessonProgress.class)
                    .setParameter("id", f.lesson()).getResultList();
                assertThat(saved).hasSize(1);
                assertThat(saved.get(0).getWatchedSeconds()).isEqualTo(100D);
                assertThat(saved.get(0).isContentCompleted()).isTrue();
            });
        } finally { start.countDown(); executor.shutdownNow(); }
    }

}
