package com.bidv.coretraining.lesson.service;
import com.bidv.coretraining.enums.DocumentPurpose;
import com.bidv.coretraining.enums.DocumentType;
import com.bidv.coretraining.learner.service.model.AuthorizedLesson;
import com.bidv.coretraining.lesson.repository.projection.LessonAccessView;

import com.bidv.coretraining.document.entity.LessonDocument;
import com.bidv.coretraining.document.repository.LessonDocumentRepository;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.lesson.dto.LessonProgressRequest;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.entity.LessonProgress;
import com.bidv.coretraining.lesson.repository.LessonProgressRepository;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.service.impl.LessonProgressServiceImpl;
import com.bidv.coretraining.policy.LessonContentPolicy;
import com.bidv.coretraining.quiz.entity.Question;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LessonProgressContentTest {
    final UserRepository users = mock(UserRepository.class);
    final LessonRepository lessons = mock(LessonRepository.class);
    final LessonProgressRepository history = mock(LessonProgressRepository.class);
    final LessonDocumentRepository documents = mock(LessonDocumentRepository.class);
    final LearningAccessService access = mock(LearningAccessService.class);
    final LessonProgressService service = spy(new LessonProgressServiceImpl(users, lessons, history, access, documents));
    final User learner = User.builder().id(1L).username("learner").build();
    final Lesson lesson = Lesson.builder().id("L1").title("Lesson").version(1L).build();
    final LessonProgress saved = LessonProgress.builder().user(learner).lesson(lesson).completionVersion(1L).build();

    @BeforeEach void setup() {
        var row = mock(LessonAccessView.class);
        when(row.getId()).thenReturn("L1");
        when(row.getVersion()).thenReturn(1L);
        when(row.getHasText()).thenAnswer(i -> lesson.getTextBody() != null && !lesson.getTextBody().isBlank());
        when(row.getHasVideo()).thenAnswer(i -> lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank());
        when(row.getTextRequired()).thenAnswer(i -> lesson.getTextRequired());
        when(row.getVideoRequired()).thenAnswer(i -> lesson.getVideoRequired());
        when(row.getSlideRequired()).thenAnswer(i -> lesson.getSlideRequired());
        when(lessons.findAccess("L1")).thenReturn(Optional.of(row));
        when(lessons.findProgressRequirements(List.of("L1"))).thenReturn(List.of(row));
        when(access.requireLesson("learner", "L1")).thenReturn(new AuthorizedLesson(learner, null, row));
        when(users.lockForLearning(1L)).thenReturn(Optional.of(1L));
        when(lessons.getReferenceById("L1")).thenReturn(lesson);
        when(history.findByUserIdAndLessonId(1L, "L1")).thenReturn(Optional.of(saved));
        when(history.findByUserId(1L)).thenReturn(List.of(saved));
        when(documents.findAllByLessonIdAndPurpose("L1", DocumentPurpose.SLIDE)).thenAnswer(i ->
                lesson.getDocuments().stream().filter(d -> d.getPurpose() == DocumentPurpose.SLIDE).map(d -> {
                    var id = mock(LessonDocumentRepository.IdOnly.class);
                    when(id.getId()).thenReturn(d.getId());
                    return id;
                }).toList());
        doNothing().when(service).recordCompletion(learner, lesson);
    }

    @Test void supportingPowerpointDoesNotBlockTextCompletion() {
        lesson.setTextBody("Text");
        lesson.getDocuments().add(LessonDocument.builder().id(10L).documentType(DocumentType.POWERPOINT).build());
        var id = mock(LessonDocumentRepository.IdOnly.class);
        when(id.getId()).thenReturn(10L);
        when(documents.findAllByLessonId("L1")).thenReturn(List.of(id));
        var request = new LessonProgressRequest();
        request.setContentType("TEXT");
        request.setTextCompleted(true);
        assertThat(service.saveProgress("learner", "L1", request).isContentCompleted()).isTrue();
        verify(service).recordCompletion(learner, lesson);
    }

    @Test void attachmentOnlyLessonCannotCompleteByReadingAttachment() {
        lesson.getDocuments().add(LessonDocument.builder().id(10L).documentType(DocumentType.POWERPOINT).build());
        var id = mock(LessonDocumentRepository.IdOnly.class);
        when(id.getId()).thenReturn(10L);
        when(documents.findAllByLessonId("L1")).thenReturn(List.of(id));
        when(documents.existsByIdAndLessonId(10L, "L1")).thenReturn(true);
        var request = new LessonProgressRequest();
        request.setContentType("DOCUMENT");
        request.setDocumentId(10L);
        request.setDocumentCompleted(true);
        assertThat(service.saveProgress("learner", "L1", request).isContentCompleted()).isFalse();
        verify(service, never()).recordCompletion(learner, lesson);
    }

    @Test void unchangedBookmarkReevaluatesOldAttachmentBlockers() {
        lesson.setTextBody("Text");
        saved.setTextCompleted(true);
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isContentCompleted()).isTrue();
    }

    @Test void readProgressReevaluatesOldAttachmentBlockers() {
        lesson.setTextBody("Text");
        saved.setTextCompleted(true);
        assertThat(service.getAllProgress(learner).get("L1").isContentCompleted()).isTrue();
        assertThat(service.contentCompleted(learner, "L1")).isTrue();
    }

    @Test void requiredQuizDoesNotBypassUnfinishedContent() {
        lesson.setTextBody("Text");
        lesson.ensureQuiz();
        lesson.getQuiz().setRequiredForProgress(true);
        lesson.getQuiz().getQuestions().add(Question.builder().build());
        assertThatThrownBy(() -> service.completeContent("learner", "L1")).isInstanceOf(BadRequestException.class);
    }

    @Test void allRequiredSlideDecksMustBeCompletedAndSupportingDeckDoesNotCount() {
        lesson.getDocuments().add(LessonDocument.builder().id(10L).documentType(DocumentType.POWERPOINT)
                .purpose(DocumentPurpose.SLIDE).build());
        lesson.getDocuments().add(LessonDocument.builder().id(11L).documentType(DocumentType.POWERPOINT)
                .purpose(DocumentPurpose.SLIDE).build());
        lesson.getDocuments().add(LessonDocument.builder().id(12L).documentType(DocumentType.POWERPOINT).build());
        var request = new LessonProgressRequest();
        request.setContentType("DOCUMENT"); request.setDocumentCompleted(true);
        for (long id : new long[] {12L, 10L, 11L}) {
            when(documents.existsByIdAndLessonId(id, "L1")).thenReturn(true);
            request.setDocumentId(id);
            assertThat(service.saveProgress("learner", "L1", request).isContentCompleted()).isEqualTo(id == 11L);
        }
        verify(service).recordCompletion(learner, lesson);
    }

    @Test void mixedContentStillRequiresTextAndNinetyPercentVideo() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        var request = new LessonProgressRequest();
        request.setContentType("TEXT"); request.setTextCompleted(true);
        assertThat(service.saveProgress("learner", "L1", request).isContentCompleted()).isFalse();
        request.setContentType("VIDEO"); request.setDurationSeconds(100D); request.setWatchedSeconds(89D);
        assertThat(service.saveProgress("learner", "L1", request).isContentCompleted()).isFalse();
        request.setWatchedSeconds(90D);
        assertThat(service.saveProgress("learner", "L1", request).isContentCompleted()).isTrue();
    }

    @Test void finishingContentAfterPassingRequiredQuizAwardsCompletion() {
        lesson.setTextBody("Text"); lesson.ensureQuiz();
        lesson.getQuiz().setRequiredForProgress(true);
        lesson.getQuiz().getQuestions().add(Question.builder().build());
        learner.getQuizScores().add(UserQuizScore.builder().user(learner).lesson(lesson).score(1).total(1).build());
        var request = new LessonProgressRequest();
        request.setContentType("TEXT"); request.setTextCompleted(true);
        assertThat(service.saveProgress("learner", "L1", request).isLessonCompleted()).isTrue();
        verify(service).recordCompletion(learner, lesson);
    }

    @Test void finishingContentWithoutPassingRequiredQuizDoesNotAwardCompletion() {
        lesson.setTextBody("Text"); lesson.ensureQuiz();
        lesson.getQuiz().setRequiredForProgress(true);
        lesson.getQuiz().getQuestions().add(Question.builder().build());
        var request = new LessonProgressRequest();
        request.setContentType("TEXT"); request.setTextCompleted(true);
        var result = service.saveProgress("learner", "L1", request);
        assertThat(result.isContentCompleted()).isTrue();
        assertThat(result.isLessonCompleted()).isFalse();
        verify(service, never()).recordCompletion(learner, lesson);
    }

    private void requiredQuiz() {
        lesson.ensureQuiz();
        lesson.getQuiz().setRequiredForProgress(true);
        lesson.getQuiz().getQuestions().add(Question.builder().build());
    }

    private void passedQuiz() {
        learner.getQuizScores().add(UserQuizScore.builder().user(learner).lesson(lesson).score(1).total(1).build());
    }

    private LessonProgressRequest textRead() {
        var request = new LessonProgressRequest();
        request.setContentType("TEXT");
        request.setTextCompleted(true);
        return request;
    }

    private LessonProgressRequest videoWatched(double seconds) {
        var request = new LessonProgressRequest();
        request.setContentType("VIDEO");
        request.setDurationSeconds(100D);
        request.setWatchedSeconds(seconds);
        return request;
    }

    private void addSlide() {
        lesson.getDocuments().add(LessonDocument.builder().id(20L).documentType(DocumentType.POWERPOINT)
                .purpose(DocumentPurpose.SLIDE).build());
    }

    @Test void optionalVideoDoesNotBlockRequiredText() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        lesson.getContent().setVideoRequired(false);
        var result = service.saveProgress("learner", "L1", textRead());
        assertThat(result.isContentCompleted()).isTrue();
        assertThat(result.isLessonCompleted()).isTrue();
        verify(service).recordCompletion(learner, lesson);
    }

    @Test void optionalTextDoesNotBlockRequiredVideo() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        lesson.getContent().setTextRequired(false);
        assertThat(service.saveProgress("learner", "L1", videoWatched(89D)).isLessonCompleted()).isFalse();
        assertThat(service.saveProgress("learner", "L1", videoWatched(90D)).isLessonCompleted()).isTrue();
    }

    @Test void optionalSlideDoesNotBlockRequiredText() {
        lesson.setTextBody("Text"); addSlide();
        lesson.getContent().setSlideRequired(false);
        assertThat(service.saveProgress("learner", "L1", textRead()).isLessonCompleted()).isTrue();
    }

    @Test void requiredSlideStillBlocksWhenOtherContentIsOptional() {
        lesson.setTextBody("Text"); addSlide();
        lesson.getContent().setTextRequired(false);
        assertThat(service.saveProgress("learner", "L1", textRead()).isContentCompleted()).isFalse();
        when(documents.existsByIdAndLessonId(20L, "L1")).thenReturn(true);
        var request = new LessonProgressRequest();
        request.setContentType("DOCUMENT"); request.setDocumentId(20L); request.setDocumentCompleted(true);
        assertThat(service.saveProgress("learner", "L1", request).isLessonCompleted()).isTrue();
    }

    @Test void lessonWithoutSavedChoiceRequiresAllContent() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4"); addSlide();
        assertThat(LessonContentPolicy.requirements(lesson))
                .isEqualTo(new LessonContentPolicy.Requirements(true, true, true, List.of(20L)));
    }

    @Test void nothingRequiredAndNoQuizWaitsForTheLearnersOwnAction() {
        lesson.setTextBody("Text");
        lesson.getContent().setTextRequired(false);
        var result = service.saveProgress("learner", "L1", new LessonProgressRequest());
        assertThat(result.isContentCompleted()).isTrue();
        assertThat(result.isLessonCompleted()).isFalse();
        verify(service, never()).recordCompletion(learner, lesson);
        assertThatCode(() -> service.completeContent("learner", "L1")).doesNotThrowAnyException();
    }

    @Test void nothingRequiredWithRequiredQuizCompletesOnlyOncePassed() {
        lesson.setTextBody("Text"); requiredQuiz();
        lesson.getContent().setTextRequired(false);
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isLessonCompleted()).isFalse();
        passedQuiz();
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isLessonCompleted()).isTrue();
    }

    @Test void nothingRequiredCountsAsContentDoneEvenWithoutSavedProgress() {
        when(history.findByUserIdAndLessonId(1L, "L1")).thenReturn(Optional.empty());
        lesson.setTextBody("Text");
        assertThat(service.contentCompleted(learner, "L1")).isFalse();
        lesson.getContent().setTextRequired(false);
        assertThat(service.contentCompleted(learner, "L1")).isTrue();
    }

    @Test void lessonWithNoContentStaysUncompletable() {
        assertThat(LessonContentPolicy.complete(lesson, null)).isFalse();
        assertThatThrownBy(() -> service.completeContent("learner", "L1")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa có nội dung");
    }

    @Test void completionErrorNamesOnlyRequiredContent() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        lesson.getContent().setVideoRequired(false);
        assertThatThrownBy(() -> service.completeContent("learner", "L1")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("văn bản").hasMessageNotContaining("video");
    }

    @Test void relaxingRequirementCompletesInProgressLearnerOnNextSave() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        assertThat(service.saveProgress("learner", "L1", textRead()).isLessonCompleted()).isFalse();
        lesson.getContent().setVideoRequired(false);
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isLessonCompleted()).isTrue();
    }

    @Test void tighteningRequirementCreditsWorkDoneWhileOptional() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        lesson.getContent().setTextRequired(false);
        when(users.hasCompletedLesson(1L, "L1")).thenReturn(false);
        requiredQuiz();
        assertThat(service.saveProgress("learner", "L1", textRead()).isContentCompleted()).isFalse();
        assertThat(service.saveProgress("learner", "L1", videoWatched(90D)).isContentCompleted()).isTrue();
        lesson.getContent().setTextRequired(true);
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isContentCompleted()).isTrue();
    }

    @Test void tighteningRequirementReopensContentButKeepsExistingAward() {
        lesson.setTextBody("Text"); lesson.setVideoUrl("/video.mp4");
        lesson.getContent().setVideoRequired(false);
        saved.setTextCompleted(true);
        saved.setContentCompleted(true);
        when(users.hasCompletedLesson(1L, "L1")).thenReturn(true);
        lesson.getContent().setVideoRequired(true);
        var result = service.saveProgress("learner", "L1", new LessonProgressRequest());
        assertThat(result.isContentCompleted()).isFalse();
        assertThat(result.isLessonCompleted()).isTrue();
        verify(service, never()).recordCompletion(learner, lesson);
    }

    @Test void existingAwardIsPreservedWhenContentChanges() {
        when(users.hasCompletedLesson(1L, "L1")).thenReturn(true);
        lesson.setTextBody("Updated text");
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isLessonCompleted()).isTrue();
        verify(service, never()).recordCompletion(learner, lesson);
    }
}
