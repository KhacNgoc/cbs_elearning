package com.bidv.coretraining.course.dto;

import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;

/** Data contracts for CourseRatingService. */
public final class CourseRatingDto {
    private CourseRatingDto() {}

    public record Request(@NotNull @Min(1) @Max(5) Integer stars,@Size(max=2000) String content,Long version) {}

    public record Rating(Long id,Long version,int stars,String content,LocalDateTime updatedAt) {}

    public record Summary(String courseId,String courseName,long count,Double average,long completed,long low,Double responseRate) {}

    public record State(boolean canRate,String message,Rating mine,Summary summary) {}

    public record Feedback(Long id,String courseId,String courseName,String learner,int stars,String content,LocalDateTime updatedAt) {}

    public record Report(List<Summary> courses,long count,Double average,long completed,long low,Double responseRate,Map<Integer,Long> distribution,List<Feedback> feedback,long totalFeedback,int page,int totalPages) {}
}
