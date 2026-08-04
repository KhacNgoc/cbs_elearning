package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CourseDocumentDto;
import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.entity.DocumentStatus;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CourseDocumentRepository;
import com.bidv.coretraining.repository.TopicRepository;
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
    private final TopicRepository topicRepository;
    private final RagIngestionService ragIngestionService;
    private final Path uploadDirectory;

    public DocumentService(CourseDocumentRepository documentRepository, TopicRepository topicRepository, RagIngestionService ragIngestionService,
                           @Value("${app.upload-directory}") String uploadDirectory) {
        this.documentRepository = documentRepository;
        this.topicRepository = topicRepository;
        this.ragIngestionService = ragIngestionService;
        this.uploadDirectory = Path.of(uploadDirectory);
    }

    public CourseDocumentDto upload(String topicId, MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null) throw new BadRequestException("Tệp tải lên không hợp lệ");
        String lowerName = file.getOriginalFilename().toLowerCase();
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx")) throw new BadRequestException("Chỉ hỗ trợ PDF hoặc DOCX");
        Topic topic = topicRepository.findById(topicId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khóa học: " + topicId));
        try {
            Files.createDirectories(uploadDirectory);
            String id = UUID.randomUUID().toString();
            Path destination = uploadDirectory.resolve(id + (lowerName.endsWith(".pdf") ? ".pdf" : ".docx"));
            file.transferTo(destination);
            CourseDocument document = documentRepository.save(CourseDocument.builder().id(id).topic(topic).fileName(file.getOriginalFilename())
                    .storagePath(destination.toAbsolutePath().toString()).contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .sizeBytes(file.getSize()).status(DocumentStatus.PENDING).createdAt(LocalDateTime.now()).build());
            ragIngestionService.ingestAsync(id);
            return CourseDocumentDto.from(document);
        } catch (IOException error) {
            throw new BadRequestException("Không thể lưu tệp: " + error.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<CourseDocumentDto> list(String topicId) {
        if (!topicRepository.existsById(topicId)) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học: " + topicId);
        }
        return documentRepository.findByTopicIdOrderByCreatedAtDesc(topicId).stream().map(CourseDocumentDto::from).toList();
    }
}
