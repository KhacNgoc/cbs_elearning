package com.bidv.coretraining.repository.projection;

/** A published chapter's place in its course, without loading the chapter. */
public interface PublishedChapterView {
    String getId();
    String getCourseId();
}
