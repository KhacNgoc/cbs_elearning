package com.bidv.coretraining.course.repository;

import com.bidv.coretraining.course.dto.CourseRatingDto;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository.Scope;
import java.util.*;

/**
 * Course rating figures within a report scope. Ratings and completions count only when the learner belongs to a
 * unit of a role that grants the report (and, for an assigned-courses-only role, the course is assigned to the
 * actor), so a unit's report never shows other units' feedback or response rates.
 */
public interface CourseRatingReportRepository {
    record CourseName(String id, String name) {}
    record Figures(long count, Double average, long low) {}

    /**
     * Courses of the scope: published courses allocated to a scope unit or one of its ancestors, and any course a
     * scope learner has rated or completed.
     */
    List<CourseName> courses(Scope scope, Map<Integer, Set<String>> grantUnits);

    Map<String, Figures> aggregate(Scope scope, Collection<String> courseIds);

    Map<String, Long> completed(Scope scope, Collection<String> courseIds);

    Map<Integer, Long> distribution(Scope scope, Collection<String> courseIds);

    List<CourseRatingDto.Feedback> feedback(Scope scope, Collection<String> courseIds, boolean lowOnly, int page, int size);

    long feedbackCount(Scope scope, Collection<String> courseIds, boolean lowOnly);
}
