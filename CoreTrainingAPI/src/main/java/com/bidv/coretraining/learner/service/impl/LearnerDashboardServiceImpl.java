package com.bidv.coretraining.learner.service.impl;
import com.bidv.coretraining.learner.dto.LearnerDashboardDto.*;
import com.bidv.coretraining.learner.dto.LearnerDashboardDto;
import com.bidv.coretraining.learner.dto.LearnerWorkspaceDto;
import com.bidv.coretraining.learner.repository.LearnerDashboardQueryRepository;
import com.bidv.coretraining.learner.repository.support.LearnerQueryScopeFactory;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.learner.service.LearnerDashboardService;
import com.bidv.coretraining.learner.service.LearnerWorkspaceService;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class LearnerDashboardServiceImpl implements LearnerDashboardService {
    private final AuthorizationService auth;
    private final LearnerDashboardQueryRepository query;
    private final LearnerWorkspaceService workspace;
    private final CourseRepository courses;
    public LearnerDashboardServiceImpl(AuthorizationService auth, LearnerDashboardQueryRepository query, LearnerWorkspaceService workspace, CourseRepository courses) {
        this.auth=auth;this.query=query;this.workspace=workspace;this.courses=courses;
    }
    public Overview overview(String tab) {
        var user=auth.current();
        var summary=query.summary(LearnerQueryScopeFactory.from(user));var choices=query.choices(LearnerQueryScopeFactory.from(user),tab);var id=query.resumeCourseId(LearnerQueryScopeFactory.from(user));
        // Hydrate only the selected course for the shared completion/content/unlock resume policy.
        var resume=id==null?null:scopedResume(user,courses.findById(id).orElseThrow());
        return new Overview(summary,choices,resume);
    }
    public LearnerWorkspaceDto.Resume resume(String id) {
        var user=auth.current();
        AccessPolicy.require(query.canResume(LearnerQueryScopeFactory.from(user),id));
        return scopedResume(user,courses.findById(id).orElseThrow());
    }
    /** Resume policy sees only the selected course's completion/score facts, never the learner's full history. */
    private LearnerWorkspaceDto.Resume scopedResume(User actor,Course course) {
        var user=User.builder().id(actor.getId()).username(actor.getUsername()).role(actor.getRole()).roles(actor.getRoles())
            .status(actor.getStatus()).organizationUnit(actor.getOrganizationUnit()).scopeUnitIds(actor.getScopeUnitIds())
            .dynamicAccessInitialized(actor.isDynamicAccessInitialized()).build();
        if (actor.isDynamicAccessInitialized()) user.setPolicySnapshot(AccessPolicy.policies(actor));
        user.getRegisteredCourses().add(course);
        var facts=query.resumeFacts(actor.getId(),course.getId());
        facts.completedLessons().forEach(id->user.getCompletedLessons().add(Lesson.builder().id(id).build()));
        facts.scores().forEach(score->user.getQuizScores().add(UserQuizScore.builder().course(course).lesson(score.lessonId()==null?null:Lesson.builder().id(score.lessonId()).build()).score(score.score()).total(score.total()).build()));
        return workspace.resumeCourse(course,user);
    }
    public LearnerDashboardDto.Page page(LearnerDashboardDto.Filter filter) { return query.page(LearnerQueryScopeFactory.from(auth.current()),filter); }
    public List<LearnerDashboardDto.CourseChoice> choices(String search,int size) { return query.courseChoices(LearnerQueryScopeFactory.from(auth.current()),search,size); }
}
