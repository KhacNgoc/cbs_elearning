package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.CourseDocumentDto;
import com.bidv.coretraining.dto.RagChatRequest;
import com.bidv.coretraining.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;
    public DocumentController(DocumentService documentService) { this.documentService = documentService; }

    @PostMapping("/admin/topics/{topicId}/documents")
    public ResponseEntity<CourseDocumentDto> upload(@PathVariable String topicId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(documentService.upload(topicId, file));
    }
    @GetMapping("/admin/topics/{topicId}/documents")
    public ResponseEntity<List<CourseDocumentDto>> list(@PathVariable String topicId) { return ResponseEntity.ok(documentService.list(topicId)); }
    @PostMapping("/rag/chat")
    public ResponseEntity<Map> chat(@Valid @RequestBody RagChatRequest request) { return ResponseEntity.ok(documentService.chat(request)); }
}
