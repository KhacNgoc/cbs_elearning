package com.bidv.coretraining.lesson.repository.projection;

/** Scalar outline row: intentionally excludes content, documents, comments and question bodies. */
public interface LessonOutlineView {
    String getId();
    String getTitle();
    String getCourseId();
    String getChapterId();
    String getChapterStatus();
    String getStatus();
    Long getVersion();
    Integer getLessonOrder();
    Boolean getHasQuiz();
    Boolean getQuizRequired();
    Integer getQuizPassingScore();
    Boolean getHasVideo();
    Boolean getHasText();
    Boolean getTextRequired();
    Boolean getVideoRequired();
    Boolean getSlideRequired();
}
