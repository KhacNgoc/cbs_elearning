package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CourseDocumentDto;
import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.entity.DocumentStatus;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CourseDocumentRepository;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.LessonKnowledgeSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final CourseDocumentRepository documentRepository;
    private final CourseRepository courseRepository;
    private final RagIngestionService ragIngestionService;
    private final LessonKnowledgeSourceRepository knowledgeSources;
    private final Path uploadDirectory;

    public DocumentService(CourseDocumentRepository documentRepository, CourseRepository courseRepository,
            RagIngestionService ragIngestionService,
            LessonKnowledgeSourceRepository knowledgeSources,
            @Value("${app.upload-directory}") String uploadDirectory) {
        this.documentRepository = documentRepository;
        this.courseRepository = courseRepository;
        this.ragIngestionService = ragIngestionService;
        this.knowledgeSources = knowledgeSources;
        this.uploadDirectory = Path.of(uploadDirectory);
    }

    public CourseDocumentDto upload(String courseId, MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null)
            throw new BadRequestException("Tệp tải lên không hợp lệ");
        String lowerName = file.getOriginalFilename().toLowerCase();
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx"))
            throw new BadRequestException("Chỉ hỗ trợ PDF hoặc DOCX");
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học: " + courseId));
        try {
            Files.createDirectories(uploadDirectory);
            String id = UUID.randomUUID().toString();
            Path destination = uploadDirectory.resolve(id + (lowerName.endsWith(".pdf") ? ".pdf" : ".docx"));
            file.transferTo(destination);
            CourseDocument document = documentRepository.save(CourseDocument.builder().id(id).course(course)
                    .fileName(file.getOriginalFilename())
                    .storagePath(destination.toAbsolutePath().toString())
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .sizeBytes(file.getSize()).status(DocumentStatus.PENDING).createdAt(LocalDateTime.now()).build());
            ragIngestionService.ingestAsync(id);
            return CourseDocumentDto.from(document);
        } catch (IOException error) {
            throw new BadRequestException("Không thể lưu tệp: " + error.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<CourseDocumentDto> list(String courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học: " + courseId);
        }
        return documentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream().map(CourseDocumentDto::from)
                .toList();
    }

    @Transactional
    public void delete(String courseId, String documentId) {
        CourseDocument document = documentRepository.findForDeletionById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu"));
        if (!document.getCourse().getId().equals(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy tài liệu trong khóa học này");
        }
        if (document.getStatus() == DocumentStatus.PENDING || document.getStatus() == DocumentStatus.INDEXING) {
            throw new BadRequestException(
                    "Tài liệu đang chờ hoặc đang embedding. Vui lòng chờ xử lý hoàn tất trước khi xóa.");
        }
        // Remove vectors first; keep the record available for retry if RAG is
        // unavailable.
        ragIngestionService.deleteIndex(documentId, courseId);
        try {
            Files.deleteIfExists(Path.of(document.getStoragePath()));
        } catch (IOException error) {
            throw new BadRequestException("Không thể xóa tệp tài liệu. Vui lòng thử lại.");
        }
        knowledgeSources.deleteByDocumentId(documentId);
        knowledgeSources.flush();
        documentRepository.delete(document);
        documentRepository.flush();
    }
}
