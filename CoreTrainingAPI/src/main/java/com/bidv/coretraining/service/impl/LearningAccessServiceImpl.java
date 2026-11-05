package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.repository.projection.LessonAccessView;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.service.model.AuthorizedLesson;
import com.bidv.coretraining.service.model.LearnableCourse;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.repository.support.LearnerQueryScopeFactory;
import com.bidv.coretraining.security.AccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hot-path access checks use ids/exists queries, never a chapter's content graph. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningAccessServiceImpl implements LearningAccessService {
    private final UserRepository users;
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final LearnerDashboardQueryRepository learnerQueries;

    public boolean requireCourse(User user, Course course) {
        AccessPolicy.require(user.activeAccount());
        boolean editor = AccessPolicy.canReadCourse(user, course);
        if (editor) return true;
        AccessPolicy.require(AccessPolicy.permissions(user).contains("LEARN") && AccessPolicy.visible(user, course));
        boolean enrolled = AccessPolicy.mandatory(user, course)
            || users.hasRegisteredCourse(user.getUsername(), course.getId())
            || users.hasMandatoryCourse(user.getUsername(), course.getId());
        if (!"PUBLISHED".equals(course.getStatus()) || !enrolled)
            throw new AccessDeniedException("Bạn chưa được cấp quyền học khóa này");
        return false;
    }

    public java.util.List<LearnableCourse> learnableCourses(User user) {
        if (!user.activeAccount()) return java.util.List.of();
        return learnerQueries.learnableCourses(LearnerQueryScopeFactory.from(user),
                AccessPolicy.permissions(user).contains("LEARN")).stream()
            .map(row -> new LearnableCourse(row.id(), row.name(), row.editor())).toList();
    }

    public AuthorizedLesson requireLesson(String username, String id) {
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User"));
        return requireLesson(user, id);
    }

    public AuthorizedLesson requireLesson(User user, String id) {
        LessonAccessView lesson = lessons.findAccess(id).orElseThrow(() -> new ResourceNotFoundException("Lesson"));
        Course course = courses.findById(lesson.getCourseId()).orElseThrow(() -> new ResourceNotFoundException("Course"));
        if (!requireCourse(user, course) && (!"PUBLISHED".equals(lesson.getStatus())
            || !"PUBLISHED".equals(lesson.getChapterStatus())
            || lessons.hasIncompletePredecessors(course.getId(), lesson.getChapterOrder(), lesson.getChapterId(),
                lesson.getLessonOrder(), id, user.getId())))
            throw new AccessDeniedException("Hoàn thành các bài và chương trước để mở bài học này");
        return new AuthorizedLesson(user, course, lesson);
    }
}
