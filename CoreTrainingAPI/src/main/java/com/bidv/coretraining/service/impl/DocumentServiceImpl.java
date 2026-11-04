package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.CourseDocumentDto;
import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.enums.DocumentStatus;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CourseDocumentRepository;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.LessonKnowledgeSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {
    private final CourseDocumentRepository documentRepository;
    private final CourseRepository courseRepository;
    private final RagIngestionService ragIngestionService;
    private final LessonKnowledgeSourceRepository knowledgeSources;
    private final Path uploadDirectory;

    public DocumentServiceImpl(CourseDocumentRepository documentRepository, CourseRepository courseRepository,
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listPaged(String courseId, String search, String statusFilter, int page, int size) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học: " + courseId);
        }
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        int pageSize = (size > 0 && size <= 100) ? size : 10;
        int pageIdx = Math.max(0, page);

        boolean filterByStatus = false;
        List<DocumentStatus> statuses = List.of(DocumentStatus.values());
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            String sf = statusFilter.trim().toUpperCase();
            if ("INDEXING".equals(sf) || "PENDING".equals(sf)) {
                filterByStatus = true;
                statuses = List.of(DocumentStatus.PENDING, DocumentStatus.INDEXING);
            } else if ("INDEXED".equals(sf)) {
                filterByStatus = true;
                statuses = List.of(DocumentStatus.INDEXED);
            } else if ("FAILED".equals(sf)) {
                filterByStatus = true;
                statuses = List.of(DocumentStatus.FAILED);
            }
        }

        Page<CourseDocument> resultPage = documentRepository.findByCourseIdWithFilter(
                courseId,
                query,
                filterByStatus,
                statuses,
                PageRequest.of(pageIdx, pageSize)
        );

        long totalAll = 0;
        long indexedCount = 0;
        long indexingCount = 0;
        long failedCount = 0;
        for (Object[] row : documentRepository.countStatusByCourseId(courseId)) {
            DocumentStatus st = (DocumentStatus) row[0];
            long cnt = ((Number) row[1]).longValue();
            totalAll += cnt;
            if (st == DocumentStatus.INDEXED) {
                indexedCount += cnt;
            } else if (st == DocumentStatus.PENDING || st == DocumentStatus.INDEXING) {
                indexingCount += cnt;
            } else if (st == DocumentStatus.FAILED) {
                failedCount += cnt;
            }
        }

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", totalAll);
        stats.put("indexed", indexedCount);
        stats.put("indexing", indexingCount);
        stats.put("failed", failedCount);

        Map<String, Object> response = new HashMap<>();
        response.put("items", resultPage.getContent().stream().map(CourseDocumentDto::from).toList());
        response.put("page", resultPage.getNumber());
        response.put("size", resultPage.getSize());
        response.put("totalElements", resultPage.getTotalElements());
        response.put("totalPages", Math.max(1, resultPage.getTotalPages()));
        response.put("hasNext", resultPage.hasNext());
        response.put("stats", stats);
        return response;
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
