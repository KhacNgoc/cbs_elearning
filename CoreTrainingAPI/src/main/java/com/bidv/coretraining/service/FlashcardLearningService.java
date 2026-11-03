package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.enums.FlashcardRating;

/** Learner flashcard decks, spaced-repetition reviews and learners' personal cards. */
public interface FlashcardLearningService {
    FlashcardDto.Deck lessonDeck(String lessonId, String username);

    FlashcardDto.Card review(Long flashcardId, FlashcardRating rating, String username);

    FlashcardDto.Summary dueSummary(String username);

    FlashcardDto.ReviewQueue dueQueue(String courseId, int limit, String username);

    FlashcardDto.Card createPersonal(String lessonId, FlashcardDto.PersonalCardRequest request, String username);

    FlashcardDto.Card updatePersonal(Long flashcardId, FlashcardDto.PersonalCardRequest request, String username);

    void deletePersonal(Long flashcardId, String username);
}
