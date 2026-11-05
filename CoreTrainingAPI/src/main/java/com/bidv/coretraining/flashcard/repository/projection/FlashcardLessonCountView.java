package com.bidv.coretraining.flashcard.repository.projection;

public interface FlashcardLessonCountView {
    String getLessonId();
    Long getTotal();
    Long getMine();
}
