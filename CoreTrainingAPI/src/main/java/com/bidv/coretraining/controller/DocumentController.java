package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.CourseDocumentDto;
import com.bidv.coretraining.dto.RagChatRequest;
import com.bidv.coretraining.dto.RagChatResponse;
import com.bidv.coretraining.dto.RagChatHistoryDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.DocumentService;
import com.bidv.coretraining.service.RagChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;
    private final RagChatService ragChatService;
    public DocumentController(DocumentService documentService, RagChatService ragChatService) {
        this.documentService = documentService;
        this.ragChatService = ragChatService;
    }

    @PostMapping("/admin/topics/{topicId}/documents")
    public ResponseEntity<CourseDocumentDto> upload(@PathVariable String topicId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(documentService.upload(topicId, file));
    }
    @GetMapping("/admin/topics/{topicId}/documents")
    public ResponseEntity<List<CourseDocumentDto>> list(@PathVariable String topicId) { return ResponseEntity.ok(documentService.list(topicId)); }
    @PostMapping("/rag/chat")
    public ResponseEntity<RagChatResponse> chat(
            @Valid @RequestBody RagChatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ragChatService.chat(userDetails.getUsername(), request));
    }

    @GetMapping("/rag/chat/history")
    public ResponseEntity<List<RagChatHistoryDto>> history(
            @RequestParam String courseId,
            @RequestParam(required = false) String conversationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ragChatService.history(userDetails.getUsername(), courseId, conversationId));
    }
}
