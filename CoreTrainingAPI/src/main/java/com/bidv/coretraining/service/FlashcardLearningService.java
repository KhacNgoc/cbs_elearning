package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardDto;

/** Learner flashcard decks and learners' personal cards. */
public interface FlashcardLearningService {
    FlashcardDto.Deck lessonDeck(String lessonId, String username);

    /** One page of courses by name; pass the previous page's nextCursor as after. */
    FlashcardDto.Library library(String after, int size, String username);

    FlashcardDto.Card createPersonal(String lessonId, FlashcardDto.PersonalCardRequest request, String username);

    FlashcardDto.Card updatePersonal(Long flashcardId, FlashcardDto.PersonalCardRequest request, String username);

    void deletePersonal(Long flashcardId, String username);
}
