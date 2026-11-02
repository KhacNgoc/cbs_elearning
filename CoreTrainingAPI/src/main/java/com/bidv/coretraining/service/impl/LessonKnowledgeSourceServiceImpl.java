package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.enums.DocumentStatus;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.DocumentOutlineDto;
import com.bidv.coretraining.dto.LessonKnowledgeSourceDto;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;

@Service
public class LessonKnowledgeSourceServiceImpl implements LessonKnowledgeSourceService {
    private final AuthorizationService authorization;
    private final LessonRepository lessons;
    private final CourseDocumentRepository documents;
    private final LessonKnowledgeSourceRepository sources;
    private final RestTemplate rest;
    private final String ragUrl;

    public LessonKnowledgeSourceServiceImpl(LessonRepository lessons, CourseDocumentRepository documents,
            LessonKnowledgeSourceRepository sources, AuthorizationService authorization, @Value("${app.rag-url}") String ragUrl) {
        this.authorization = authorization;
        this.lessons = lessons;
        this.documents = documents;
        this.sources = sources;
        this.ragUrl = ragUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.rest = new RestTemplate(factory);
    }

    @Transactional(readOnly = true)
    public List<LessonKnowledgeSourceDto> listAuthorized(String lessonId) {
        authorization.edit(lessons.findById(lessonId).orElseThrow().getCourse());
        return list(lessonId);
    }

    @Transactional
    public List<LessonKnowledgeSourceDto> saveAuthorized(String lessonId, List<LessonKnowledgeSourceDto> request) {
        authorization.edit(lessons.findById(lessonId).orElseThrow().getCourse());
        return save(lessonId, request);
    }

    @Transactional(readOnly = true)
    public DocumentOutlineDto outlineAuthorized(String courseId, String documentId) {
        authorization.edit(courseId);
        return outline(courseId, documentId);
    }

    @Transactional(readOnly = true)
    public List<LessonKnowledgeSourceDto> list(String lessonId) {
        requireLesson(lessonId);
        return sources.findByLessonIdOrderByIdAsc(lessonId).stream().map(this::dto).toList();
    }

    @Transactional
    public List<LessonKnowledgeSourceDto> save(String lessonId, List<LessonKnowledgeSourceDto> request) {
        Lesson lesson = requireLesson(lessonId);
        if (!"DRAFT".equals(lesson.getCourse().getStatus())) {
            throw new BadRequestException("Chuyển khóa học về bản nháp để chỉnh sửa nguồn của bài học.");
        }
        if (request.size() > 50)
            throw new BadRequestException("Chỉ chọn tối đa 50 phạm vi tài liệu.");
        Set<LessonKnowledgeSourceDto> unique = new HashSet<>();
        List<LessonKnowledgeSource> selected = new ArrayList<>();
        Map<String, DocumentOutlineDto> outlines = new HashMap<>();
        for (LessonKnowledgeSourceDto selection : request) {
            if (selection == null || selection.documentId() == null || selection.documentId().isBlank()
                    || (selection.section() != null && selection.section().length() > 2000)) {
                throw new BadRequestException("Nguồn tài liệu không hợp lệ.");
            }
            if (!unique.add(selection))
                throw new BadRequestException("Phạm vi tài liệu bị trùng.");
            CourseDocument document = requireDocument(lesson.getCourse().getId(), selection.documentId());
            DocumentOutlineDto outline = outlines.computeIfAbsent(document.getId(),
                    id -> outline(document.getCourse().getId(), id));
            validateSelection(document, outline, selection);
            selected.add(LessonKnowledgeSource.builder().lesson(lesson).document(document)
                    .pageFrom(selection.pageFrom()).pageTo(selection.pageTo())
                    .section(selection.section() == null || selection.section().isBlank() ? null : selection.section())
                    .build());
        }
        // The lesson version serializes concurrent edits to its source assignments.
        lesson.setUpdatedAt(java.time.LocalDateTime.now());
        lesson.getKnowledgeSources().clear();
        lesson.getKnowledgeSources().addAll(selected);
        lessons.saveAndFlush(lesson);
        return selected.stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public DocumentOutlineDto outline(String courseId, String documentId) {
        requireDocument(courseId, documentId);
        try {
            var uri = UriComponentsBuilder.fromHttpUrl(ragUrl).pathSegment("documents", documentId, "outline")
                    .queryParam("course_id", courseId).build().encode().toUri();
            DocumentOutlineDto outline = rest.getForObject(uri, DocumentOutlineDto.class);
            if (outline == null || outline.pages() == null || outline.pages().isEmpty()
                    || outline.sections() == null || outline.excerpts() == null) {
                throw new BadRequestException("Tài liệu chưa có nội dung đã xử lý. Hãy chọn tài liệu khác.");
            }
            return outline;
        } catch (RestClientException error) {
            throw new RagServiceUnavailableException("Không đọc được nội dung tài liệu. Vui lòng thử lại.", error);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> generationSources(Lesson lesson) {
        List<LessonKnowledgeSource> assigned = sources.findByLessonIdOrderByIdAsc(lesson.getId());
        if (assigned.isEmpty()) {
            throw new BadRequestException(
                    "Bài học chưa có nguồn kiến thức. Hãy chọn và lưu nguồn tài liệu trước khi tạo câu hỏi.");
        }
        return assigned.stream().map(source -> {
            requireDocument(lesson.getCourse().getId(), source.getDocument().getId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("document_id", source.getDocument().getId());
            if (source.getPageFrom() != null)
                entry.put("page_from", source.getPageFrom());
            if (source.getPageTo() != null)
                entry.put("page_to", source.getPageTo());
            if (source.getSection() != null)
                entry.put("section", source.getSection());
            return entry;
        }).toList();
    }

    private void validateSelection(CourseDocument document, DocumentOutlineDto outline,
            LessonKnowledgeSourceDto source) {
        boolean range = source.pageFrom() != null || source.pageTo() != null;
        boolean section = source.section() != null && !source.section().isBlank();
        boolean pdf = document.getFileName().toLowerCase(Locale.ROOT).endsWith(".pdf");
        if (range) {
            if (!pdf || section || source.pageFrom() == null || source.pageTo() == null
                    || source.pageFrom() < 1 || source.pageFrom() > source.pageTo()
                    || source.pageTo() > Collections.max(outline.pages())
                    || outline.pages().stream()
                            .noneMatch(page -> page >= source.pageFrom() && page <= source.pageTo())) {
                throw new BadRequestException("Khoảng trang PDF không hợp lệ hoặc không có nội dung đã xử lý.");
            }
        }
        if (section && (pdf || !outline.sections().contains(source.section()))) {
            throw new BadRequestException("Mục đã chọn không tồn tại trong tài liệu Word.");
        }
    }

    private Lesson requireLesson(String id) {
        return lessons.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
    }

    private CourseDocument requireDocument(String courseId, String documentId) {
        CourseDocument document = documents.findWithCourseById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseDocument", "id", documentId));
        if (!document.getCourse().getId().equals(courseId))
            throw new BadRequestException("Tài liệu không thuộc khóa học của bài.");
        if (document.getStatus() != DocumentStatus.INDEXED)
            throw new BadRequestException("Tài liệu chưa sẵn sàng. Hãy chờ xử lý xong.");
        return document;
    }

    private LessonKnowledgeSourceDto dto(LessonKnowledgeSource source) {
        return new LessonKnowledgeSourceDto(source.getDocument().getId(), source.getPageFrom(), source.getPageTo(),
                source.getSection());
    }
}
