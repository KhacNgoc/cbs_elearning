package com.bidv.coretraining.service;

import com.bidv.coretraining.service.model.AuthorizedLesson;
import com.bidv.coretraining.entity.*;

/** Business operations for LearningAccessService. */
public interface LearningAccessService {
    public boolean requireCourse(User user, Course course);

    public AuthorizedLesson requireLesson(String username, String id);

    public AuthorizedLesson requireLesson(User user, String id);
}
