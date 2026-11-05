package com.bidv.coretraining.learner.service.model;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.lesson.repository.projection.LessonAccessView;
import com.bidv.coretraining.user.entity.User;

/** Internal authorization result; never serialized as an API response. */
public record AuthorizedLesson(User user, Course course, LessonAccessView lesson) {}
