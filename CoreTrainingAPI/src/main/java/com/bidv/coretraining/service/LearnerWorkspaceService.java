package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LearnerWorkspaceDto.*;
import com.bidv.coretraining.entity.*;
import java.time.*;
import java.util.*;

/** Business operations for LearnerWorkspaceService. */
public interface LearnerWorkspaceService {
    public Workspace workspace();

    public Resume resumeCourse(Course course, User user);
}
