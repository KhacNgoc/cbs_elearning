package com.bidv.coretraining.lesson.repository.projection;

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
    Boolean getTextRequired();
    Boolean getVideoRequired();
    Boolean getSlideRequired();
    Long getVersion();
}
