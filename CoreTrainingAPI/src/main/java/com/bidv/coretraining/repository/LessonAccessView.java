package com.bidv.coretraining.repository;

public interface LessonAccessView {
    String getId();
    String getCourseId();
    String getChapterId();
    String getStatus();
    String getChapterStatus();
    Integer getLessonOrder();
    Integer getChapterOrder();
    Boolean getHasVideo();
    Boolean getHasText();
    Long getVersion();
}
