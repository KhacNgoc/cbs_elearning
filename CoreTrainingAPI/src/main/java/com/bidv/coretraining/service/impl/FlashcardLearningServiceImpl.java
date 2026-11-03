package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.Flashcard;
import com.bidv.coretraining.entity.FlashcardReview;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.enums.FlashcardRating;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.policy.FlashcardSchedulingPolicy;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.FlashcardRepository;
import com.bidv.coretraining.repository.FlashcardReviewRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.projection.FlashcardCourseDueView;
import com.bidv.coretraining.service.FlashcardLearningService;
import com.bidv.coretraining.service.LearningAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashcardLearningServiceImpl implements FlashcardLearningService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final int MAX_QUEUE = 50;

    private final FlashcardRepository flashcards;
    private final FlashcardReviewRepository reviews;
    private final UserRepository users;
    private final CourseRepository courses;
    private final LearningAccessService learningAccess;

    @Override
    @Transactional(readOnly = true)
    public FlashcardDto.Deck lessonDeck(String lessonId, String username) {
        User user = learningAccess.requireLesson(username, lessonId).user();
        List<Flashcard> cards = flashcards.findByLessonIdAndStatusOrderByCardOrderAscIdAsc(lessonId, PUBLISHED);
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
            result.add(toCard(card, review, now));
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
        return toCard(card, reviews.save(review), now);
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
                due.stream().map(review -> toCard(review.getFlashcard(), review, now)).toList());
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

    private FlashcardDto.Card toCard(Flashcard card, FlashcardReview review, LocalDateTime now) {
        FlashcardDto.Progress progress = review == null ? null
                : new FlashcardDto.Progress(review.getDueAt().atZone(ZoneId.systemDefault()).toInstant(),
                        review.getIntervalDays(), review.getRepetitions(),
                        review.getLapses(), review.getLastRating(), !review.getDueAt().isAfter(now),
                        FlashcardSchedulingPolicy.mastered(review.getIntervalDays()));
        return new FlashcardDto.Card(card.getId(), card.getCourse().getId(), card.getLesson().getId(),
                card.getLesson().getTitle(), card.getType(), card.getFront(), card.getBack(),
                FlashcardAdminServiceImpl.source(card), progress);
    }
}
