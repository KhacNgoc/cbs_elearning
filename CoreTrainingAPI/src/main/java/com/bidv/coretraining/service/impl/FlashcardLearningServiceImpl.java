package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.Flashcard;
import com.bidv.coretraining.entity.FlashcardReview;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.enums.FlashcardRating;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.policy.FlashcardSchedulingPolicy;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.FlashcardRepository;
import com.bidv.coretraining.repository.FlashcardReviewRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.projection.FlashcardCourseDueView;
import com.bidv.coretraining.service.FlashcardLearningService;
import com.bidv.coretraining.service.LearningAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashcardLearningServiceImpl implements FlashcardLearningService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final int MAX_QUEUE = 50;
    static final int MAX_PERSONAL_PER_LESSON = 200;

    private final FlashcardRepository flashcards;
    private final FlashcardReviewRepository reviews;
    private final UserRepository users;
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final LearningAccessService learningAccess;

    @Override
    @Transactional(readOnly = true)
    public FlashcardDto.Deck lessonDeck(String lessonId, String username) {
        User user = learningAccess.requireLesson(username, lessonId).user();
        // Lesson cards in authored order, then the learner's own cards in the order they wrote them.
        List<Flashcard> cards = new ArrayList<>(
                flashcards.findByLessonIdAndOwnerIsNullAndStatusOrderByCardOrderAscIdAsc(lessonId, PUBLISHED));
        cards.addAll(flashcards.findByLessonIdAndOwnerIdOrderByCreatedAtAscIdAsc(lessonId, user.getId()));
        Map<Long, FlashcardReview> byCard = reviews.findForLesson(user.getId(), lessonId).stream()
                .collect(Collectors.toMap(review -> review.getFlashcard().getId(), Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        int fresh = 0, due = 0, learning = 0, mastered = 0;
        List<FlashcardDto.Card> result = new ArrayList<>(cards.size());
        for (Flashcard card : cards) {
            FlashcardReview review = byCard.get(card.getId());
            if (review == null) {
                fresh++;
            } else if (!review.getDueAt().isAfter(now)) {
                due++;
            } else if (FlashcardSchedulingPolicy.mastered(review.getIntervalDays())) {
                mastered++;
            } else {
                learning++;
            }
            result.add(toCard(card, review, now, user));
        }
        return new FlashcardDto.Deck(lessonId, result,
                new FlashcardDto.Stats(cards.size(), fresh, due, learning, mastered));
    }

    @Override
    @Transactional
    public FlashcardDto.Card review(Long flashcardId, FlashcardRating rating, String username) {
        Flashcard card = flashcards.findById(flashcardId)
                .filter(found -> PUBLISHED.equals(found.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", "id", flashcardId));
        User user = learningAccess.requireLesson(username, card.getLesson().getId()).user();
        if (card.getOwner() != null && !card.getOwner().getId().equals(user.getId()))
            throw new ResourceNotFoundException("Flashcard", "id", flashcardId);
        LocalDateTime now = LocalDateTime.now();
        FlashcardReview review = reviews.findByUserIdAndFlashcardId(user.getId(), flashcardId)
                .orElseGet(() -> FlashcardReview.builder().user(user).flashcard(card).build());
        FlashcardSchedulingPolicy.State current = review.getId() == null ? null
                : new FlashcardSchedulingPolicy.State(review.getIntervalDays(), review.getEaseFactor(),
                        review.getRepetitions(), review.getLapses());
        FlashcardSchedulingPolicy.Schedule next = FlashcardSchedulingPolicy.next(current, rating, now);
        review.setIntervalDays(next.state().intervalDays());
        review.setEaseFactor(next.state().easeFactor());
        review.setRepetitions(next.state().repetitions());
        review.setLapses(next.state().lapses());
        review.setDueAt(next.dueAt());
        review.setLastRating(rating);
        review.setLastReviewedAt(now);
        return toCard(card, reviews.save(review), now, user);
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardDto.Summary dueSummary(String username) {
        User user = user(username);
        List<FlashcardDto.CourseDue> due = new ArrayList<>();
        for (FlashcardCourseDueView row : reviews.dueByCourse(user.getId(), LocalDateTime.now())) {
            Course course = courses.findById(row.getCourseId()).orElse(null);
            if (course == null || !canLearn(user, course))
                continue;
            due.add(new FlashcardDto.CourseDue(row.getCourseId(), row.getCourseName(), row.getDue().intValue()));
        }
        return new FlashcardDto.Summary(due.stream().mapToInt(FlashcardDto.CourseDue::due).sum(), due);
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardDto.ReviewQueue dueQueue(String courseId, int limit, String username) {
        User user = user(username);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        learningAccess.requireCourse(user, course);
        LocalDateTime now = LocalDateTime.now();
        int size = Math.max(1, Math.min(MAX_QUEUE, limit));
        List<FlashcardReview> due = reviews.findDueInCourse(user.getId(), courseId, now, PageRequest.of(0, size));
        int dueTotal = reviews.dueByCourse(user.getId(), now).stream()
                .filter(row -> courseId.equals(row.getCourseId()))
                .mapToInt(row -> row.getDue().intValue()).findFirst().orElse(due.size());
        return new FlashcardDto.ReviewQueue(course.getId(), course.getName(), dueTotal,
                due.stream().map(review -> toCard(review.getFlashcard(), review, now, user)).toList());
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
        return toCard(flashcards.saveAndFlush(card), null, now, user);
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
        LocalDateTime now = LocalDateTime.now();
        card.setUpdatedAt(now);
        // Flush so the returned version matches the row for the next optimistic update.
        Flashcard saved = flashcards.saveAndFlush(card);
        return toCard(saved, reviews.findByUserIdAndFlashcardId(user.getId(), flashcardId).orElse(null), now, user);
    }

    @Override
    @Transactional
    public void deletePersonal(Long flashcardId, String username) {
        // No lesson access check: learners can always remove what they wrote.
        Flashcard card = personal(flashcardId, user(username));
        reviews.deleteByFlashcardId(card.getId());
        flashcards.delete(card);
    }

    /** Another learner's card reads as missing so its existence is not revealed. */
    private Flashcard personal(Long flashcardId, User user) {
        return flashcards.findByIdAndOwnerId(flashcardId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Flashcard", "id", flashcardId));
    }

    private boolean canLearn(User user, Course course) {
        try {
            learningAccess.requireCourse(user, course);
            return true;
        } catch (AccessDeniedException denied) {
            return false;
        }
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private FlashcardDto.Card toCard(Flashcard card, FlashcardReview review, LocalDateTime now, User user) {
        FlashcardDto.Progress progress = review == null ? null
                : new FlashcardDto.Progress(review.getDueAt().atZone(ZoneId.systemDefault()).toInstant(),
                        review.getIntervalDays(), review.getRepetitions(),
                        review.getLapses(), review.getLastRating(), !review.getDueAt().isAfter(now),
                        FlashcardSchedulingPolicy.mastered(review.getIntervalDays()));
        return new FlashcardDto.Card(card.getId(), card.getCourse().getId(), card.getLesson().getId(),
                card.getLesson().getTitle(), card.getType(), card.getFront(), card.getBack(),
                FlashcardAdminServiceImpl.source(card), progress, mine(card, user),
                mine(card, user) ? card.getVersion() : null);
    }

    private static boolean mine(Flashcard card, User user) {
        return card.getOwner() != null && card.getOwner().getId().equals(user.getId());
    }
}
