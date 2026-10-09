package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.entity.DocumentStatus;
import com.bidv.coretraining.repository.CourseDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class RagIngestionService {
    private final CourseDocumentRepository documentRepository;
    private final String ragUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public RagIngestionService(CourseDocumentRepository documentRepository, @Value("${app.rag-url}") String ragUrl) {
        this.documentRepository = documentRepository;
        this.ragUrl = ragUrl;
    }

    @Async
    public void ingestAsync(String documentId) {
        CourseDocument document = documentRepository.findWithCourseById(documentId).orElseThrow();
        document.setStatus(DocumentStatus.INDEXING);
        documentRepository.save(document);
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("document_id", document.getId());
            body.add("course_id", document.getCourse().getId());
            body.add("file", new FileSystemResource(document.getStoragePath()) {
                @Override
                public String getFilename() {
                    return document.getFileName();
                }
            });
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map> response = restTemplate.postForEntity(ragUrl + "/ingest", new HttpEntity<>(body, headers), Map.class);
            Map responseBody = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || responseBody == null
                    || !(responseBody.get("chunks_indexed") instanceof Number chunks)) {
                throw new IllegalStateException("Dịch vụ embedding trả về dữ liệu không hợp lệ");
            }
            document.setIndexedChunks(chunks.intValue());
            document.setStatus(DocumentStatus.INDEXED);
            document.setIndexedAt(LocalDateTime.now());
            document.setErrorMessage(null);
        } catch (Exception error) {
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(error.getMessage());
        }
        documentRepository.save(document);
    }
}
