package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LearnerDashboardDto.CourseChoice;
import java.util.List;

/** Courses whose indexed documents the learning assistant may search for a user. */
public interface RagCourseScopeService {
    public List<CourseChoice> searchableCourses(String username);
}
