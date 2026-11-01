package com.bidv.coretraining.controller;
import com.bidv.coretraining.dto.RagChatDto;
import com.bidv.coretraining.service.RagChatService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
@RestController
public class RagHistoryController {
 private final RagChatService service;
 public RagHistoryController(RagChatService service){this.service=service;}
 @GetMapping("/api/rag/chat/history/page")
 public RagChatDto.HistoryPage history(@AuthenticationPrincipal UserDetails user,@RequestParam String courseId,@RequestParam(required=false) String conversationId,@RequestParam(required=false) Long beforeId,@RequestParam(defaultValue="20") int size){return service.historyPage(user.getUsername(),courseId,conversationId,beforeId,size);}
}
