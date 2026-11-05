package com.bidv.coretraining.lesson.service;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.document.repository.CourseDocumentRepository;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonKnowledgeSourceRepository;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.service.impl.LessonKnowledgeSourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSourceAuthorizationTest {
    @Test void deniesLessonAccessBeforeReadingSourcesOrChangingAssignments() {
        var lessons = mock(LessonRepository.class);
        var documents = mock(CourseDocumentRepository.class);
        var sources = mock(LessonKnowledgeSourceRepository.class);
        var auth = mock(AuthorizationService.class);
        var course = Course.builder().id("course").build();
        when(lessons.findById("lesson")).thenReturn(Optional.of(Lesson.builder().id("lesson").course(course).build()));
        doThrow(new AccessDeniedException("denied")).when(auth).edit(course);
        var service = new LessonKnowledgeSourceServiceImpl(lessons, documents, sources, auth, "http://127.0.0.1:1");
        assertThatThrownBy(() -> service.listAuthorized("lesson")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveAuthorized("lesson", List.of())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(documents, sources);
        verify(lessons, never()).saveAndFlush(any());
    }

    @Test void deniesOutlineBeforeDocumentLookupAndRemoteRequest() {
        var lessons = mock(LessonRepository.class);
        var documents = mock(CourseDocumentRepository.class);
        var sources = mock(LessonKnowledgeSourceRepository.class);
        var auth = mock(AuthorizationService.class);
        doThrow(new AccessDeniedException("denied")).when(auth).edit("course");
        var service = new LessonKnowledgeSourceServiceImpl(lessons, documents, sources, auth, "http://127.0.0.1:1");
        assertThatThrownBy(() -> service.outlineAuthorized("course", "missing"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lessons, documents, sources);
    }
}
