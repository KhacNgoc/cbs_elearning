package com.bidv.coretraining.repository.projection;

import com.bidv.coretraining.enums.FlashcardType;

/** The columns a learner's study session shows for one card. */
public interface FlashcardCardView {
    Long getId();
    String getCourseId();
    String getLessonId();
    String getLessonTitle();
    FlashcardType getType();
    String getFront();
    String getBack();
    String getSourceDocumentId();
    String getSourceDocumentName();
    Integer getSourcePage();
    String getSourceSection();
    Long getOwnerId();
    Long getVersion();
}
