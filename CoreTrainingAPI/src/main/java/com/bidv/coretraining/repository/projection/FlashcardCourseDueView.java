package com.bidv.coretraining.repository.projection;

public interface FlashcardCourseDueView {
    String getCourseId();
    String getCourseName();
    Long getDue();
}
