package com.bidv.coretraining.rag.service;

import com.bidv.coretraining.learner.dto.LearnerDashboardDto.CourseChoice;
import java.util.List;

/** Courses whose indexed documents the learning assistant may search for a user. */
public interface RagCourseScopeService {
    public List<CourseChoice> searchableCourses(String username);
}
