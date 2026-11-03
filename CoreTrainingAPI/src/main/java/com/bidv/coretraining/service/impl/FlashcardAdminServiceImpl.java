package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.dto.FlashcardGenerationJobDto;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.entity.Flashcard;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.enums.FlashcardType;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CourseDocumentRepository;
import com.bidv.coretraining.repository.FlashcardRepository;
import com.bidv.coretraining.repository.FlashcardReviewRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.service.AuthorizationService;
import com.bidv.coretraining.service.FlashcardAdminService;
import com.bidv.coretraining.service.FlashcardGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FlashcardAdminServiceImpl implements FlashcardAdminService {
    /** A cloze card hides its answer behind a run of at least three underscores. */
    static final Pattern CLOZE_BLANK = Pattern.compile("_{3,}");

    private final AuthorizationService authorization;
    private final LessonRepository lessons;
    private final FlashcardRepository flashcards;
    private final FlashcardReviewRepository reviews;
    private final CourseDocumentRepository documents;
    private final FlashcardGenerationService generation;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional(readOnly = true)
    public List<FlashcardDto.AdminCard> list(String lessonId) {
        Lesson lesson = lesson(lessonId);
        authorization.edit(lesson.getCourse());
        return flashcards.findByLessonIdOrderByCardOrderAscIdAsc(lessonId).stream()
                .map(FlashcardAdminServiceImpl::toAdminCard).toList();
    }

    @Override
    @Transactional
    public FlashcardDto.AdminCard create(String lessonId, FlashcardDto.CardRequest request) {
        Lesson lesson = writableLesson(lessonId);
        return toAdminCard(flashcards.save(newCard(lesson, request, flashcards.maxCardOrder(lessonId) + 1)));
    }

    @Override
    @Transactional
    public List<FlashcardDto.AdminCard> createBatch(String lessonId, FlashcardDto.BatchRequest request) {
        Lesson lesson = writableLesson(lessonId);
        int order = flashcards.maxCardOrder(lessonId) + 1;
        List<Flashcard> created = new ArrayList<>();
        for (FlashcardDto.CardRequest card : request.cards())
            created.add(newCard(lesson, card, order++));
        return flashcards.saveAll(created).stream().map(FlashcardAdminServiceImpl::toAdminCard).toList();
    }

    @Override
    @Transactional
    public FlashcardDto.AdminCard update(String lessonId, Long id, FlashcardDto.CardRequest request) {
        Lesson lesson = writableLesson(lessonId);
        Flashcard card = card(lessonId, id);
        if (request.version() != null && !Objects.equals(card.getVersion(), request.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dữ liệu đã thay đổi. Hãy tải lại trang");
        apply(card, lesson.getCourse(), request);
        if (request.status() != null)
            card.setStatus(request.status());
        card.setUpdatedAt(LocalDateTime.now());
        // Flush so the returned version matches the row for the next optimistic update.
        return toAdminCard(flashcards.saveAndFlush(card));
    }

    @Override
    @Transactional
    public void delete(String lessonId, Long id) {
        writableLesson(lessonId);
        Flashcard card = card(lessonId, id);
        reviews.deleteByFlashcardId(card.getId());
        flashcards.delete(card);
    }

    @Override
    @Transactional
    public List<FlashcardDto.AdminCard> updateStatus(String lessonId, FlashcardDto.StatusRequest request) {
        writableLesson(lessonId);
        List<Flashcard> cards = flashcards.findByLessonIdAndIdIn(lessonId, new HashSet<>(request.ids()));
        if (cards.size() != new HashSet<>(request.ids()).size())
            throw new BadRequestException("Có thẻ không thuộc bài học này");
        LocalDateTime now = LocalDateTime.now();
        for (Flashcard card : cards) {
            card.setStatus(request.status());
            card.setUpdatedAt(now);
        }
        return flashcards.saveAllAndFlush(cards).stream().map(FlashcardAdminServiceImpl::toAdminCard).toList();
    }

    // The two generation methods read the DB in a short transaction and call the RAG service
    // outside it, so a slow AI service cannot hold pooled connections other requests need.
    @Override
    public FlashcardGenerationJobDto startGeneration(String lessonId, FlashcardDto.GenerationRequest request) {
        FlashcardGenerationService.Target target =
                readOnly().execute(status -> generation.target(writableLesson(lessonId)));
        return generation.startJob(target, request);
    }

    @Override
    public FlashcardGenerationJobDto generationStatus(String lessonId, String jobId) {
        if (!lessonId.equals(generation.jobLessonId(jobId)))
            throw new ResourceNotFoundException("Không tìm thấy tiến trình tạo flashcard");
        readOnly().executeWithoutResult(status -> authorization.edit(lesson(lessonId).getCourse()));
        return generation.jobStatus(jobId);
    }

    private TransactionTemplate readOnly() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    private Flashcard newCard(Lesson lesson, FlashcardDto.CardRequest request, int order) {
        LocalDateTime now = LocalDateTime.now();
        Flashcard card = Flashcard.builder()
                .course(lesson.getCourse())
                .lesson(lesson)
                .origin(request.origin() == null ? FlashcardOrigin.MANUAL : request.origin())
                .status(request.status() == null ? "DRAFT" : request.status())
                .cardOrder(order)
                .createdAt(now)
                .updatedAt(now)
                .build();
        apply(card, lesson.getCourse(), request);
        return card;
    }

    private void apply(Flashcard card, Course course, FlashcardDto.CardRequest request) {
        String front = request.front().trim();
        String back = request.back().trim();
        if (front.isEmpty() || back.isEmpty())
            throw new BadRequestException("Mặt trước và mặt sau của thẻ không được để trống");
        if (request.type() == FlashcardType.CLOZE) {
            if (!CLOZE_BLANK.matcher(front).find())
                throw new BadRequestException("Thẻ điền khuyết cần có chỗ trống ___ ở mặt trước");
            if (back.length() >= 4 && front.toLowerCase().contains(back.toLowerCase()))
                throw new BadRequestException("Đáp án của thẻ điền khuyết đang lộ ở mặt trước");
        }
        card.setType(request.type());
        card.setFront(front);
        card.setBack(back);
        String documentId = blankToNull(request.sourceDocumentId());
        if (documentId == null) {
            card.setSourceDocumentId(null);
            card.setSourceDocumentName(null);
            card.setSourcePage(null);
            card.setSourceSection(null);
            return;
        }
        CourseDocument document = documents.findWithCourseById(documentId)
                .filter(found -> found.getCourse().getId().equals(course.getId()))
                .orElseThrow(() -> new BadRequestException("Tài liệu nguồn không thuộc khóa học"));
        card.setSourceDocumentId(document.getId());
        // Display name comes from the stored document, never from the client.
        card.setSourceDocumentName(document.getFileName());
        card.setSourcePage(request.sourcePage());
        card.setSourceSection(blankToNull(request.sourceSection()));
    }

    private Lesson writableLesson(String lessonId) {
        Lesson lesson = lesson(lessonId);
        authorization.edit(lesson.getCourse());
        String courseStatus = lesson.getCourse().getStatus();
        if ("DELETED".equals(courseStatus) || "DELETED".equals(lesson.getStatus()))
            throw new BadRequestException("Bài học đã bị xóa");
        if ("ARCHIVED".equals(courseStatus))
            throw new BadRequestException("Khóa học đang lưu trữ. Hãy chuyển về nháp để chỉnh sửa flashcard");
        return lesson;
    }

    private Lesson lesson(String lessonId) {
        return lessons.findById(lessonId).orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
    }

    private Flashcard card(String lessonId, Long id) {
        return flashcards.findByIdAndLessonId(id, lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", "id", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static FlashcardDto.Source source(Flashcard card) {
        return card.getSourceDocumentId() == null ? null
                : new FlashcardDto.Source(card.getSourceDocumentId(), card.getSourceDocumentName(),
                        card.getSourcePage(), card.getSourceSection());
    }

    private static FlashcardDto.AdminCard toAdminCard(Flashcard card) {
        return new FlashcardDto.AdminCard(card.getId(), card.getLesson().getId(), card.getType(), card.getFront(),
                card.getBack(), card.getOrigin(), card.getStatus(), source(card), card.getCardOrder(),
                card.getUpdatedAt(), card.getVersion());
    }
}
