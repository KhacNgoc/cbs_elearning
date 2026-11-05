package com.bidv.coretraining.flashcard.service.impl;

import com.bidv.coretraining.course.repository.ChapterRepository;
import com.bidv.coretraining.course.repository.projection.PublishedChapterView;
import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.flashcard.dto.FlashcardDto;
import com.bidv.coretraining.flashcard.entity.Flashcard;
import com.bidv.coretraining.flashcard.repository.FlashcardRepository;
import com.bidv.coretraining.flashcard.repository.projection.FlashcardCardView;
import com.bidv.coretraining.flashcard.repository.projection.FlashcardLessonCountView;
import com.bidv.coretraining.flashcard.service.FlashcardLearningService;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.learner.service.model.LearnableCourse;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.repository.projection.LessonOutlineView;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashcardLearningServiceImpl implements FlashcardLearningService {
    private static final String PUBLISHED = "PUBLISHED";
    static final int MAX_PERSONAL_PER_LESSON = 200;
    private static final int MAX_LIBRARY_PAGE = 50;
    private static final int MAX_LIBRARY_ROUNDS = 5;

    private final FlashcardRepository flashcards;
    private final UserRepository users;
    private final LessonRepository lessons;
    private final ChapterRepository chapters;
    private final LearningAccessService learningAccess;

    @Override
    @Transactional(readOnly = true)
    public FlashcardDto.Deck lessonDeck(String lessonId, String username) {
        User user = learningAccess.requireLesson(username, lessonId).user();
        // Lesson cards in authored order, then the learner's own cards in the order they wrote them.
        List<Flashcard> cards = new ArrayList<>(
                flashcards.findByLessonIdAndOwnerIsNullAndStatusOrderByCardOrderAscIdAsc(lessonId, PUBLISHED));
        cards.addAll(flashcards.findByLessonIdAndOwnerIdOrderByCreatedAtAscIdAsc(lessonId, user.getId()));
        return new FlashcardDto.Deck(lessonId, cards.stream().map(card -> toCard(card, user)).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardDto.Library library(String after, int size, String username) {
        User user = user(username);
        int limit = Math.max(1, Math.min(size, MAX_LIBRARY_PAGE));
        List<LearnableCourse> learnable = sortedLearnable(user);
        int position = after == null ? 0 : positionAfter(learnable, after);
        List<OpenCourse> page = new ArrayList<>();
        // Courses without an open lesson drop out of a page, so read on, but only a few times per request.
        for (int round = 0; round < MAX_LIBRARY_ROUNDS && page.size() < limit && position < learnable.size(); round++) {
            int end = Math.min(position + limit - page.size(), learnable.size());
            page.addAll(openCourses(user, learnable.subList(position, end)));
            position = end;
        }
        String nextCursor = position < learnable.size() ? learnable.get(position - 1).id() : null;
        if (page.isEmpty())
            return new FlashcardDto.Library(List.of(), nextCursor);
        List<String> courseIds = page.stream().map(item -> item.course().id()).toList();
        Map<String, FlashcardLessonCountView> counts = flashcards.countByLesson(courseIds, user.getId()).stream()
                .collect(Collectors.toMap(FlashcardLessonCountView::getLessonId, Function.identity()));
        return new FlashcardDto.Library(page.stream().map(item -> new FlashcardDto.LibraryCourse(item.course().id(),
                item.course().name(), item.lessons().stream()
                        .map(row -> libraryLesson(row, counts.get(row.getId()))).toList())).toList(), nextCursor);
    }

    /** A course dropped since the previous page restarts the list; the client ignores courses it already has. */
    private static int positionAfter(List<LearnableCourse> learnable, String courseId) {
        for (int i = 0; i < learnable.size(); i++)
            if (learnable.get(i).id().equals(courseId))
                return i + 1;
        return 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> openLessonIds(Collection<String> lessonIds, String username) {
        if (lessonIds.isEmpty())
            return List.of();
        User user = user(username);
        Set<String> courseIds = lessons.findCourseIdsOf(lessonIds);
        List<LearnableCourse> learnable = sortedLearnable(user).stream()
                .filter(course -> courseIds.contains(course.id())).toList();
        Set<String> wanted = new HashSet<>(lessonIds);
        return openCourses(user, learnable).stream().flatMap(item -> item.lessons().stream())
                .map(LessonOutlineView::getId).filter(wanted::contains).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlashcardDto.Card> cardsInLessons(List<String> lessonIds, String username) {
        if (lessonIds.isEmpty())
            return List.of();
        Long userId = user(username).getId();
        Map<String, List<FlashcardCardView>> byLesson = flashcards.findVisibleInLessons(lessonIds, userId).stream()
                .collect(Collectors.groupingBy(FlashcardCardView::getLessonId));
        return lessonIds.stream().flatMap(id -> byLesson.getOrDefault(id, List.of()).stream())
                .map(card -> toCard(card, userId)).toList();
    }

    private record OpenCourse(LearnableCourse course, List<LessonOutlineView> lessons) {
    }

    private List<LearnableCourse> sortedLearnable(User user) {
        List<LearnableCourse> learnable = new ArrayList<>(learningAccess.learnableCourses(user));
        learnable.sort(Comparator.comparing(LearnableCourse::name, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LearnableCourse::id));
        return learnable;
    }

    /** These courses with their open lessons, dropping those with none; lookups run per batch, not per course. */
    private List<OpenCourse> openCourses(User user, List<LearnableCourse> learnable) {
        if (learnable.isEmpty())
            return List.of();
        List<String> courseIds = learnable.stream().map(LearnableCourse::id).toList();
        List<String> lockedCourseIds = learnable.stream().filter(item -> !item.editor())
                .map(LearnableCourse::id).toList();
        Map<String, List<LessonOutlineView>> outlines = lessons.findOutlines(courseIds, false).stream()
                .collect(Collectors.groupingBy(LessonOutlineView::getCourseId));
        Set<String> completed = lockedCourseIds.isEmpty() ? Set.of()
                : users.findCompletedLessonIdsInCourses(user.getId(), lockedCourseIds);
        Map<String, List<String>> chaptersByCourse = lockedCourseIds.isEmpty() ? Map.of()
                : chapters.findPublishedOrder(lockedCourseIds).stream().collect(Collectors.groupingBy(
                        PublishedChapterView::getCourseId,
                        Collectors.mapping(PublishedChapterView::getId, Collectors.toList())));
        List<OpenCourse> result = new ArrayList<>();
        for (LearnableCourse item : learnable) {
            List<LessonOutlineView> rows = outlines.getOrDefault(item.id(), List.of());
            List<LessonOutlineView> open = item.editor() ? rows
                    : openRows(rows, chaptersByCourse.getOrDefault(item.id(), List.of()), completed);
            if (!open.isEmpty())
                result.add(new OpenCourse(item, open));
        }
        return result;
    }

    private static List<LessonOutlineView> openRows(List<LessonOutlineView> rows, List<String> chapterIds,
            Set<String> completed) {
        Map<String, List<String>> byChapter = rows.stream().collect(Collectors.groupingBy(
                LessonOutlineView::getChapterId, Collectors.mapping(LessonOutlineView::getId, Collectors.toList())));
        Set<String> unlocked = LearningPolicy.unlockedLessonIds(chapterIds, byChapter, completed);
        return rows.stream().filter(row -> unlocked.contains(row.getId())).toList();
    }

    private static FlashcardDto.LibraryLesson libraryLesson(LessonOutlineView row, FlashcardLessonCountView count) {
        return new FlashcardDto.LibraryLesson(row.getId(), row.getTitle(),
                count == null ? 0 : count.getTotal().intValue(), count == null ? 0 : count.getMine().intValue());
    }

    @Override
    @Transactional
    public FlashcardDto.Card createPersonal(String lessonId, FlashcardDto.PersonalCardRequest request,
            String username) {
        User user = learningAccess.requireLesson(username, lessonId).user();
        if (flashcards.countByLessonIdAndOwnerId(lessonId, user.getId()) >= MAX_PERSONAL_PER_LESSON)
            throw new BadRequestException("Mỗi bài học chỉ tạo được tối đa " + MAX_PERSONAL_PER_LESSON
                    + " thẻ của bạn");
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        LocalDateTime now = LocalDateTime.now();
        // Personal cards are live at once: there is no one to approve them.
        Flashcard card = Flashcard.builder()
                .course(lesson.getCourse())
                .lesson(lesson)
                .owner(user)
                .origin(FlashcardOrigin.PERSONAL)
                .status(PUBLISHED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        FlashcardAdminServiceImpl.applyContent(card, request.type(), request.front(), request.back());
        return toCard(flashcards.saveAndFlush(card), user);
    }

    @Override
    @Transactional
    public FlashcardDto.Card updatePersonal(Long flashcardId, FlashcardDto.PersonalCardRequest request,
            String username) {
        User user = user(username);
        Flashcard card = personal(flashcardId, user);
        learningAccess.requireLesson(user, card.getLesson().getId());
        if (request.version() != null && !Objects.equals(card.getVersion(), request.version()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thẻ đã được sửa ở nơi khác. Hãy tải lại");
        FlashcardAdminServiceImpl.applyContent(card, request.type(), request.front(), request.back());
        card.setUpdatedAt(LocalDateTime.now());
        // Flush so the returned version matches the row for the next optimistic update.
        Flashcard saved = flashcards.saveAndFlush(card);
        return toCard(saved, user);
    }

    @Override
    @Transactional
    public void deletePersonal(Long flashcardId, String username) {
        // No lesson access check: learners can always remove what they wrote.
        Flashcard card = personal(flashcardId, user(username));
        flashcards.delete(card);
    }

    /** Another learner's card reads as missing so its existence is not revealed. */
    private Flashcard personal(Long flashcardId, User user) {
        return flashcards.findByIdAndOwnerId(flashcardId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", "id", flashcardId));
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private FlashcardDto.Card toCard(Flashcard card, User user) {
        return new FlashcardDto.Card(card.getId(), card.getCourse().getId(), card.getLesson().getId(),
                card.getLesson().getTitle(), card.getType(), card.getFront(), card.getBack(),
                FlashcardAdminServiceImpl.source(card), mine(card, user),
                mine(card, user) ? card.getVersion() : null);
    }

    private static FlashcardDto.Card toCard(FlashcardCardView card, Long userId) {
        boolean mine = userId.equals(card.getOwnerId());
        FlashcardDto.Source source = card.getSourceDocumentId() == null ? null
                : new FlashcardDto.Source(card.getSourceDocumentId(), card.getSourceDocumentName(),
                        card.getSourcePage(), card.getSourceSection());
        return new FlashcardDto.Card(card.getId(), card.getCourseId(), card.getLessonId(), card.getLessonTitle(),
                card.getType(), card.getFront(), card.getBack(), source, mine, mine ? card.getVersion() : null);
    }

    private static boolean mine(Flashcard card, User user) {
        return card.getOwner() != null && card.getOwner().getId().equals(user.getId());
    }
}
