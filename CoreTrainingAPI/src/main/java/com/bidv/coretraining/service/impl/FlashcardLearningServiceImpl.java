package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.entity.Flashcard;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.enums.FlashcardOrigin;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.FlashcardRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.service.FlashcardLearningService;
import com.bidv.coretraining.service.LearningAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FlashcardLearningServiceImpl implements FlashcardLearningService {
    private static final String PUBLISHED = "PUBLISHED";
    static final int MAX_PERSONAL_PER_LESSON = 200;

    private final FlashcardRepository flashcards;
    private final UserRepository users;
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
        return new FlashcardDto.Deck(lessonId, cards.stream().map(card -> toCard(card, user)).toList());
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

    private static boolean mine(Flashcard card, User user) {
        return card.getOwner() != null && card.getOwner().getId().equals(user.getId());
    }
}
