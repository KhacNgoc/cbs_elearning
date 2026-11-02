package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.RagChatDto.*;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.RagServiceUnavailableException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class RagChatServiceImpl implements RagChatService {
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final RagConversationRepository conversationRepository;
    private final RagConversationMessageRepository messageRepository;
    private final RagChatMessageRepository legacyMessageRepository;
    private final RagConversationSummaryService summaryService;
    private final TrainingStructureService structure;
    private final RestTemplate restTemplate;
    private final String ragUrl;
    private final int recentMessageLimit;
    private final int recentCharacterLimit;

    public RagChatServiceImpl(UserRepository userRepository,
            CourseRepository courseRepository,
            RagConversationRepository conversationRepository,
            RagConversationMessageRepository messageRepository,
            RagChatMessageRepository legacyMessageRepository,
            RagConversationSummaryService summaryService,
            TrainingStructureService structure,
            @Value("${app.rag-url}") String ragUrl,
            @Value("${app.rag.recent-message-limit:8}") int recentMessageLimit,
            @Value("${app.rag.recent-character-limit:12000}") int recentCharacterLimit,
            @Value("${app.rag.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.rag.read-timeout-ms:300000}") int readTimeoutMs) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.legacyMessageRepository = legacyMessageRepository;
        this.summaryService = summaryService;
        this.structure = structure;
        this.ragUrl = ragUrl;
        int cappedRecentLimit = Math.max(2, Math.min(recentMessageLimit, 30));
        this.recentMessageLimit = cappedRecentLimit % 2 == 0 ? cappedRecentLimit : cappedRecentLimit - 1;
        this.recentCharacterLimit = Math.max(1000, recentCharacterLimit);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public RagChatResponse chat(String username, RagChatRequest request) {
        long startedAt = System.nanoTime();
        User user = requireUser(username);
        Course course = requireAccessibleCourse(user, request.courseId());
        RagConversation conversation = resolveConversation(user, course, request.conversationId());

        String question = request.question().trim();
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(question.substring(0, Math.min(question.length(), 120)));
            touch(conversation);
        }
        RagConversationMessage userMessage = messageRepository.save(RagConversationMessage.builder()
                .conversation(conversation)
                .role("user")
                .content(question)
                .originalQuestion(question)
                .createdAt(LocalDateTime.now())
                .build());
        touch(conversation);

        List<RagConversationMessage> recent = recentBefore(conversation.getId(), userMessage.getId());
        Map<String, Object> pythonRequest = new LinkedHashMap<>();
        pythonRequest.put("question", question);
        pythonRequest.put("course_id", course.getId());
        pythonRequest.put("course_name", course.getName());
        if (request.page() != null)
            pythonRequest.put("page", request.page());
        pythonRequest.put("conversation_summary", Objects.toString(conversation.getSummary(), ""));
        pythonRequest.put("recent_messages", recent.stream()
                .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
                .toList());

        RagServiceChatResponse ragResponse;
        try {
            ragResponse = restTemplate.postForObject(
                    ragUrl + "/chat", pythonRequest, RagServiceChatResponse.class);
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            if (status == 502 || status == 504) {
                throw new ResponseStatusException(
                        status == 502 ? HttpStatus.BAD_GATEWAY : HttpStatus.GATEWAY_TIMEOUT,
                        status == 502 ? "Trợ lý chưa tạo được câu trả lời hoàn chỉnh. Vui lòng thử lại."
                                : "Trợ lý chưa hoàn tất câu trả lời trong thời gian cho phép. Vui lòng thử lại.",
                        exception);
            }
            throw new RagServiceUnavailableException("Trợ lý học tập tạm thời không khả dụng", exception);
        } catch (RestClientException exception) {
            log.error("RAG chat unavailable conversationId={} userId={} courseId={} errorType={}",
                    conversation.getId(), user.getId(), course.getId(), exception.getClass().getSimpleName());
            throw new RagServiceUnavailableException("Trợ lý học tập tạm thời không khả dụng", exception);
        }
        if (ragResponse == null || ragResponse.answer() == null) {
            throw new RagServiceUnavailableException("Dịch vụ trợ lý trả về dữ liệu không hợp lệ");
        }

        List<RagSourceDto> sources = ragResponse.sources() == null ? List.of() : ragResponse.sources();
        RagConversationMessage assistantMessage = RagConversationMessage.builder()
                .conversation(conversation)
                .role("assistant")
                .content(ragResponse.answer())
                .grounded(ragResponse.grounded())
                .originalQuestion(question)
                .standaloneQuestion(ragResponse.standaloneQuestion())
                .needsClarification(ragResponse.needsClarification())
                .createdAt(LocalDateTime.now())
                .build();
        for (RagSourceDto source : sources) {
            assistantMessage.addSource(RagConversationSource.builder()
                    .documentId(source.documentId())
                    .documentName(source.documentName())
                    .page(source.page())
                    .section(source.section())
                    .build());
        }
        messageRepository.save(assistantMessage);
        touch(conversation);
        try {
            summaryService.updateAsync(conversation.getId(), user.getId(), course.getId());
        } catch (RuntimeException exception) {
            log.warn("Conversation summary dispatch failed conversationId={} userId={} courseId={} errorType={}",
                    conversation.getId(), user.getId(), course.getId(), exception.getClass().getSimpleName());
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "RAG chat completed conversationId={} userId={} courseId={} grounded={} clarification={} sources={} elapsedMs={}",
                conversation.getId(), user.getId(), course.getId(), ragResponse.grounded(),
                ragResponse.needsClarification(), sources.size(), elapsedMs);
        return new RagChatResponse(ragResponse.answer(), ragResponse.grounded(), sources,
                conversation.getId(), ragResponse.needsClarification());
    }

    @Transactional(readOnly=true)
    public List<RagChatHistoryDto> history(String username,String courseId,String conversationId) {
        return historyPage(username,courseId,conversationId,null,50).items();
    }
    @Transactional(readOnly=true)
    public HistoryPage historyPage(String username,String courseId,String conversationId,Long beforeId,int size) {
        User user=requireUser(username);Course course=requireAccessibleCourse(user,courseId);
        Optional<RagConversation> selected=conversationId==null||conversationId.isBlank()
            ? conversationRepository.findFirstByUserIdAndCourseIdOrderByUpdatedAtDesc(user.getId(),course.getId())
            : Optional.of(requireOwnedConversation(user,course,conversationId));
        int limit=Math.max(1,Math.min(50,size));long before=beforeId==null?Long.MAX_VALUE:beforeId;
        var page=org.springframework.data.domain.PageRequest.of(0,limit+1);
        if(selected.isEmpty()) {
            var ids=legacyMessageRepository.findHistoryIds(user.getId(),course.getId(),before,page);
            boolean more=ids.size()>limit;var kept=ids.subList(0,Math.min(limit,ids.size()));
            var records=kept.isEmpty()?List.<RagChatMessage>of():legacyMessageRepository.findHistoryMessages(kept);
            var items=records.stream().sorted(Comparator.comparing(RagChatMessage::getId)).map(message->new RagChatHistoryDto(message.getId(),null,courseId,message.getQuestion(),message.getAnswer(),message.isGrounded(),false,message.getSources().stream().map(source->new RagSourceDto(source.getDocumentId(),source.getDocumentName(),source.getPage(),source.getSection())).toList(),message.getCreatedAt())).toList();
            return new HistoryPage(items,more?kept.get(kept.size()-1):null,more);
        }
        var conversation=selected.get();var pairs=messageRepository.findExchangeIds(conversation.getId(),before,page);
        boolean more=pairs.size()>limit;var kept=pairs.subList(0,Math.min(limit,pairs.size()));
        Set<Long> ids=new HashSet<>();kept.forEach(pair->{ids.add(((Number)pair[0]).longValue());ids.add(((Number)pair[1]).longValue());});
        Map<Long,RagConversationMessage> messages=new HashMap<>();if(!ids.isEmpty())messageRepository.findHistoryMessages(ids).forEach(m->messages.put(m.getId(),m));
        List<RagChatHistoryDto> result=new ArrayList<>();for(var pair:kept){var answer=messages.get(((Number)pair[0]).longValue());var question=messages.get(((Number)pair[1]).longValue());result.add(new RagChatHistoryDto(answer.getId(),conversation.getId(),courseId,question.getContent(),answer.getContent(),Boolean.TRUE.equals(answer.getGrounded()),Boolean.TRUE.equals(answer.getNeedsClarification()),answer.getSources().stream().map(source->new RagSourceDto(source.getDocumentId(),source.getDocumentName(),source.getPage(),source.getSection())).toList(),answer.getCreatedAt()));}
        Collections.reverse(result);return new HistoryPage(result,more?((Number)kept.get(kept.size()-1)[0]).longValue():null,more);
    }

    private RagConversation resolveConversation(User user, Course course, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return requireOwnedConversation(user, course, conversationId);
        }
        LocalDateTime now = LocalDateTime.now();
        return conversationRepository.save(RagConversation.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .course(course)
                .summary("")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private RagConversation requireOwnedConversation(User user, Course course, String conversationId) {
        RagConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Cuộc hội thoại không thuộc người dùng hiện tại");
        }
        if (!conversation.getCourse().getId().equals(course.getId())) {
            throw new BadRequestException("Cuộc hội thoại thuộc một khóa học khác");
        }
        return conversation;
    }

    private List<RagConversationMessage> recentBefore(String conversationId, Long currentMessageId) {
        List<RagConversationMessage> descending = messageRepository
                .findTop30ByConversationIdAndIdLessThanOrderByIdDesc(conversationId, currentMessageId);
        List<RagConversationMessage> selected = new ArrayList<>();
        int characters = 0;
        for (RagConversationMessage message : descending) {
            if (selected.size() >= recentMessageLimit
                    || characters + message.getContent().length() > recentCharacterLimit)
                break;
            selected.add(message);
            characters += message.getContent().length();
        }
        Collections.reverse(selected);
        // Avoid starting the window with an orphan assistant response.
        if (!selected.isEmpty() && "assistant".equals(selected.get(0).getRole()))
            selected.remove(0);
        return selected;
    }

    private void touch(RagConversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private Course requireAccessibleCourse(User user, String courseId) {
        // Chat intentionally has no long transaction around the external AI call.
        // Reload and check learning relationships in a short service transaction.
        structure.requireCourse(courseId, user.getUsername());
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        return course;
    }

}
