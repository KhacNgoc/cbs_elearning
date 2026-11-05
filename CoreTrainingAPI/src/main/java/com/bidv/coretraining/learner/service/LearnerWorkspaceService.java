package com.bidv.coretraining.learner.service;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.learner.dto.LearnerWorkspaceDto.*;
import com.bidv.coretraining.user.entity.User;
import java.time.*;
import java.util.*;

/** Business operations for LearnerWorkspaceService. */
public interface LearnerWorkspaceService {
    public Workspace workspace();

    public Resume resumeCourse(Course course, User user);
}
