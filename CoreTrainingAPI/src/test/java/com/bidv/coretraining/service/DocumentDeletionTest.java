package com.bidv.coretraining.service;
import com.bidv.coretraining.enums.DocumentStatus;
import com.bidv.coretraining.service.impl.*;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentDeletionTest {
    @TempDir Path directory;
    CourseDocumentRepository documents;
    LessonKnowledgeSourceRepository sources;
    RagIngestionService rag;
    DocumentService service;
    CourseDocument document;

    @BeforeEach void setup() throws Exception {
        documents = mock(CourseDocumentRepository.class);
        sources = mock(LessonKnowledgeSourceRepository.class);
        rag = mock(RagIngestionService.class);
        service = new DocumentServiceImpl(documents, mock(CourseRepository.class), rag, sources, directory.toString());
        Path file = Files.writeString(directory.resolve("doc.docx"), "test");
        document = CourseDocument.builder().id("D1").course(Course.builder().id("C1").build())
                .storagePath(file.toString()).status(DocumentStatus.INDEXED).build();
        when(documents.findForDeletionById("D1")).thenReturn(Optional.of(document));
    }

    @Test void removesVectorsFileAndLessonReferencesBeforeRecord() {
        service.delete("C1", "D1");
        assertThat(Path.of(document.getStoragePath())).doesNotExist();
        var order = inOrder(rag, sources, documents);
        order.verify(rag).deleteIndex("D1", "C1");
        order.verify(sources).deleteByDocumentId("D1");
        order.verify(sources).flush();
        order.verify(documents).delete(document);
    }

    @Test void wrongCourseCannotDeleteDocument() {
        assertThatThrownBy(() -> service.delete("C2", "D1")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(rag, sources);
        assertThat(Path.of(document.getStoragePath())).exists();
        verify(documents, never()).delete(any());
    }

    @Test void activeIngestionCannotRaceDeletion() {
        for (var status : new DocumentStatus[]{DocumentStatus.PENDING, DocumentStatus.INDEXING}) {
            document.setStatus(status);
            assertThatThrownBy(() -> service.delete("C1", "D1")).isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(rag, sources);
        verify(documents, never()).delete(any());
    }

    @Test void ragFailureKeepsFileAndRecordForRetry() {
        doThrow(new RagServiceUnavailableException("offline")).when(rag).deleteIndex("D1", "C1");
        assertThatThrownBy(() -> service.delete("C1", "D1")).isInstanceOf(RagServiceUnavailableException.class);
        assertThat(Path.of(document.getStoragePath())).exists();
        verifyNoInteractions(sources);
        verify(documents, never()).delete(any());
    }

    @Test void missingFileDoesNotPreventCleanupOfFailedDocument() throws Exception {
        Files.delete(Path.of(document.getStoragePath()));
        document.setStatus(DocumentStatus.FAILED);
        service.delete("C1", "D1");
        verify(rag).deleteIndex("D1", "C1");
        verify(documents).delete(document);
    }
}
