package com.bidv.coretraining.flashcard.service;

import com.bidv.coretraining.flashcard.dto.FlashcardDto;

/** Learner flashcard decks and learners' personal cards. */
public interface FlashcardLearningService {
    FlashcardDto.Deck lessonDeck(String lessonId, String username);

    /** One page of courses by name; pass the previous page's nextCursor as after. */
    FlashcardDto.Library library(String after, int size, String username);

    /** Those of these lessons the learner can open right now, in course order. */
    java.util.List<String> openLessonIds(java.util.Collection<String> lessonIds, String username);

    /** The learner's cards in these lessons, ordered as given. Callers must have checked lesson access. */
    java.util.List<FlashcardDto.Card> cardsInLessons(java.util.List<String> lessonIds, String username);

    FlashcardDto.Card createPersonal(String lessonId, FlashcardDto.PersonalCardRequest request, String username);

    FlashcardDto.Card updatePersonal(Long flashcardId, FlashcardDto.PersonalCardRequest request, String username);

    void deletePersonal(Long flashcardId, String username);
}
