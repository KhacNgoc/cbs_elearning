package com.bidv.coretraining.repository.projection;

public interface FlashcardLessonCountView {
    String getLessonId();
    Long getTotal();
    Long getMine();
}
