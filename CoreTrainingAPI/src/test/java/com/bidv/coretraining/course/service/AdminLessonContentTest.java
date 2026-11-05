package com.bidv.coretraining.course.service;
import com.bidv.coretraining.enums.DocumentPurpose;
import com.bidv.coretraining.enums.DocumentType;
import com.bidv.coretraining.mapper.CourseMapper;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.course.service.impl.CourseContentAdminServiceImpl;
import com.bidv.coretraining.document.dto.LessonDocumentDto;
import com.bidv.coretraining.document.entity.LessonDocument;
import com.bidv.coretraining.document.service.AssetAccessService;
import com.bidv.coretraining.document.service.DocumentStorageService;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.training.service.TrainingStructureService;
import com.bidv.coretraining.video.service.VideoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminLessonContentTest {
    final LessonRepository lessons = mock(LessonRepository.class);
    final CourseContentAdminServiceImpl service = new CourseContentAdminServiceImpl(mock(AuthorizationService.class),
            mock(AssetAccessService.class), mock(CourseRepository.class), lessons, mock(CourseMapper.class),
            mock(TrainingStructureService.class), mock(DocumentStorageService.class), mock(VideoStorageService.class));
    final Lesson lesson = Lesson.builder().id("L1").title("Lesson").course(Course.builder().id("C1").build()).build();
    final LessonDocument original = LessonDocument.builder().id(10L).name("Slides").lesson(lesson)
            .documentType(DocumentType.POWERPOINT).fileUrl("/uploads/documents/a.pptx").documentOrder(0).build();
    final LessonDto request = LessonDto.builder().title("Lesson").build();

    @BeforeEach void setup() {
        when(lessons.findById("L1")).thenReturn(Optional.of(lesson));
        when(lessons.saveAndFlush(lesson)).thenReturn(lesson);
        lesson.getDocuments().add(original);
    }

    LessonDocumentDto document(Long id, String path) {
        return LessonDocumentDto.builder().id(id).name("Slides").documentType(DocumentType.POWERPOINT)
                .fileUrl(path).build();
    }

    @Test void unrelatedSavePreservesExistingDocumentIdentity() {
        request.setDocuments(List.of(document(10L, original.getFileUrl())));
        service.updateLesson("L1", request);
        assertThat(lesson.getDocuments()).containsExactly(original);
        assertThat(lesson.getDocuments().get(0).getId()).isEqualTo(10L);
    }

    @Test void replacedFileGetsFreshIdentity() {
        request.setDocuments(List.of(document(10L, "/uploads/documents/new.pptx")));
        service.updateLesson("L1", request);
        assertThat(lesson.getDocuments().get(0).getId()).isNull();
        assertThat(lesson.getDocuments().get(0)).isNotSameAs(original);
    }

    @Test void foreignDocumentIdIsRejected() {
        request.setDocuments(List.of(document(99L, original.getFileUrl())));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class);
    }

    @Test void duplicateDocumentIdIsRejected() {
        request.setDocuments(List.of(document(10L, original.getFileUrl()), document(10L, original.getFileUrl())));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class);
    }

    @Test void wordCannotBeSubmittedAsRequiredSlide() {
        var slide = document(null, "/uploads/documents/guide.docx");
        slide.setDocumentType(DocumentType.WORD);
        slide.setPurpose(DocumentPurpose.SLIDE);
        request.setDocuments(List.of(slide));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class);
    }

    @Test void legacyPptCannotBeSubmittedAsRequiredSlide() {
        var slide = document(null, "/uploads/documents/guide.ppt");
        slide.setPurpose(DocumentPurpose.SLIDE);
        request.setDocuments(List.of(slide));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class);
    }

    @Test void promotingSupportingFileToSlideGetsFreshIdentity() {
        var slide = document(10L, original.getFileUrl());
        slide.setPurpose(DocumentPurpose.SLIDE);
        request.setDocuments(List.of(slide));
        service.updateLesson("L1", request);
        assertThat(lesson.getDocuments().get(0).getId()).isNull();
        assertThat(lesson.getDocuments().get(0).getPurpose()).isEqualTo(DocumentPurpose.SLIDE);
    }

    @Test void publishedLessonCannotBeEmptiedOrContainOnlyAttachments() {
        lesson.setStatus("PUBLISHED");
        request.setDocuments(List.of(document(10L, original.getFileUrl())));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class);
    }

    @Test void emptyRichTextDoesNotMakeSupportingOnlyLessonPublishable() {
        lesson.setStatus("PUBLISHED");
        request.setTextBody("<p><br></p><p>&nbsp;</p>");
        request.setDocuments(List.of(document(10L, original.getFileUrl())));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class);
    }

    @Test void emptyRichTextInDraftIsStoredAsEmptyContent() {
        request.setTextBody("<p><br></p><p>&nbsp;</p>");
        service.updateLesson("L1", request);
        assertThat(lesson.getTextBody()).isNullOrEmpty();
    }

    @Test void rejectsMoreThanOneRequiredSlideFile() {
        var first = document(null, "/uploads/documents/first.pptx");
        first.setPurpose(DocumentPurpose.SLIDE);
        var second = document(null, "/uploads/documents/second.pptx");
        second.setPurpose(DocumentPurpose.SLIDE);
        request.setDocuments(List.of(first, second));
        assertThatThrownBy(() -> service.updateLesson("L1", request)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("một");
    }

    @Test void allowsOneRequiredSlideWithMultipleSupportingPowerpoints() {
        var slide = document(null, "/uploads/documents/slide.pptx");
        slide.setPurpose(DocumentPurpose.SLIDE);
        request.setDocuments(List.of(slide, document(null, "/uploads/documents/guide.pptx"),
                document(null, "/uploads/documents/notes.pptx")));
        service.updateLesson("L1", request);
        assertThat(lesson.getDocuments()).hasSize(3);
        assertThat(lesson.getDocuments().stream().filter(d -> d.getPurpose() == DocumentPurpose.SLIDE)).hasSize(1);
    }
}
