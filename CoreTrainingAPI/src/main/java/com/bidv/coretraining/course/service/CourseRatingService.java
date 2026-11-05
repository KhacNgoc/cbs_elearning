package com.bidv.coretraining.course.service;

import com.bidv.coretraining.course.dto.CourseRatingDto.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import java.util.*;

/** Business operations for CourseRatingService. */
public interface CourseRatingService {
    public State state(String courseId);

    public State save(String courseId,Request request);

    public Report report(String courseId,boolean lowOnly,int page);
}
