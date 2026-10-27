package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonProgressRequest;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
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
    final LessonProgressService service = spy(new LessonProgressService(users, lessons, history));
    final User learner = User.builder().id(1L).username("learner").build();
    final Lesson lesson = Lesson.builder().id("L1").title("Lesson").version(1L).build();
    final LessonProgress saved = LessonProgress.builder().user(learner).lesson(lesson).completionVersion(1L).build();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "documents", documents);
        var row = mock(LessonAccessView.class);
        when(row.getId()).thenReturn("L1");
        when(row.getVersion()).thenReturn(1L);
        when(row.getHasText()).thenAnswer(i -> lesson.getTextBody() != null && !lesson.getTextBody().isBlank());
        when(row.getHasVideo()).thenAnswer(i -> lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank());
        when(lessons.findAccess("L1")).thenReturn(Optional.of(row));
        when(lessons.findProgressRequirements(List.of("L1"))).thenReturn(List.of(row));
        when(access.requireLesson("learner", "L1")).thenReturn(new LearningAccessService.AuthorizedLesson(learner, null, row));
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

    @Test void existingAwardIsPreservedWhenContentChanges() {
        when(users.hasCompletedLesson(1L, "L1")).thenReturn(true);
        lesson.setTextBody("Updated text");
        assertThat(service.saveProgress("learner", "L1", new LessonProgressRequest()).isLessonCompleted()).isTrue();
        verify(service, never()).recordCompletion(learner, lesson);
    }
}
