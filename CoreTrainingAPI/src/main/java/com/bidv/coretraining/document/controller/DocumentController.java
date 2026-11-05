package com.bidv.coretraining.document.controller;

import com.bidv.coretraining.access.service.AuthorizationService;

import com.bidv.coretraining.document.dto.CourseDocumentDto;
import com.bidv.coretraining.document.service.DocumentService;
import com.bidv.coretraining.rag.dto.RagChatHistoryDto;
import com.bidv.coretraining.rag.dto.RagChatRequest;
import com.bidv.coretraining.rag.dto.RagChatResponse;
import com.bidv.coretraining.rag.service.RagChatService;
import com.bidv.coretraining.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final AuthorizationService authorization;
    private final DocumentService documentService;
    private final RagChatService ragChatService;

    public DocumentController(DocumentService documentService, RagChatService ragChatService,
            AuthorizationService authorization) {
        this.documentService = documentService;
        this.ragChatService = ragChatService;
        this.authorization = authorization;
    }

    @GetMapping("/admin/courses/search")
    public ResponseEntity<List<Map<String, Object>>> searchCourses(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> result = authorization.searchEditableCourses(search, limit).stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("code", c.getCode());
                    m.put("name", c.getName());
                    m.put("status", c.getStatus());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/courses/{courseId}/documents")
    public ResponseEntity<CourseDocumentDto> upload(@PathVariable String courseId,
            @RequestParam("file") MultipartFile file) {
        authorization.edit(courseId);
        return ResponseEntity.accepted().body(documentService.upload(courseId, file));
    }

    @GetMapping("/admin/courses/{courseId}/documents")
    public ResponseEntity<List<CourseDocumentDto>> list(@PathVariable String courseId) {
        authorization.edit(courseId);
        return ResponseEntity.ok(documentService.list(courseId));
    }

    @GetMapping("/admin/courses/{courseId}/documents/paged")
    public ResponseEntity<Map<String, Object>> listPaged(
            @PathVariable String courseId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        authorization.edit(courseId);
        return ResponseEntity.ok(documentService.listPaged(courseId, search, status, page, size));
    }

    @DeleteMapping("/admin/courses/{courseId}/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String courseId, @PathVariable String documentId) {
        authorization.edit(courseId);
        documentService.delete(courseId, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rag/chat")
    public ResponseEntity<RagChatResponse> chat(
            @Valid @RequestBody RagChatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ragChatService.chat(userDetails.getUsername(), request));
    }

    @GetMapping("/rag/chat/history")
    public ResponseEntity<List<RagChatHistoryDto>> history(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String conversationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ragChatService.history(userDetails.getUsername(), courseId, conversationId));
    }
}
