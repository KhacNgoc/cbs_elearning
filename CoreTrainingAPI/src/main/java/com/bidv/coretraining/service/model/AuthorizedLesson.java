package com.bidv.coretraining.service.model;

import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.repository.projection.LessonAccessView;

/** Internal authorization result; never serialized as an API response. */
public record AuthorizedLesson(User user, Course course, LessonAccessView lesson) {}
