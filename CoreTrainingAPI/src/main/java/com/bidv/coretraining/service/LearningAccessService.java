package com.bidv.coretraining.service;

import com.bidv.coretraining.service.model.AuthorizedLesson;
import com.bidv.coretraining.service.model.LearnableCourse;
import com.bidv.coretraining.entity.*;

/** Business operations for LearningAccessService. */
public interface LearningAccessService {
    public boolean requireCourse(User user, Course course);

    /** Every published course that requireCourse would allow, as scalars from one query. */
    public java.util.List<LearnableCourse> learnableCourses(User user);

    public AuthorizedLesson requireLesson(String username, String id);

    public AuthorizedLesson requireLesson(User user, String id);
}
