package com.bidv.coretraining.service;

import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class LearnerDashboardService {
    public record Overview(LearnerDashboardQuery.Summary summary,Map<String,List<Map<String,String>>> choices,LearnerWorkspaceService.Resume resume) {}
    private final AuthorizationService auth;
    private final LearnerDashboardQuery query;
    private final LearnerWorkspaceService workspace;
    private final NamedParameterJdbcTemplate jdbc;
    private final CourseRepository courses;
    public LearnerDashboardService(AuthorizationService auth,LearnerDashboardQuery query,LearnerWorkspaceService workspace,NamedParameterJdbcTemplate jdbc,CourseRepository courses) {
        this.auth=auth;this.query=query;this.workspace=workspace;this.jdbc=jdbc;this.courses=courses;
    }
    public Overview overview(String tab) {
        var user=auth.current();
        var summary=query.summary(user);var choices=query.choices(user,tab);var id=query.resumeCourseId(user);
        // Hydrate only the selected course for the shared completion/content/unlock resume policy.
        var resume=id==null?null:scopedResume(user,courses.findById(id).orElseThrow());
        return new Overview(summary,choices,resume);
    }
    public LearnerWorkspaceService.Resume resume(String id) {
        var user=auth.current();var ctx=query.facts(user);var params=new HashMap<>(ctx.params());params.put("courseId",id);
        var count=jdbc.queryForObject(ctx.sql()+" select count(*) from facts where id=:courseId and source in ('ASSIGNED','REGISTERED')",params,Integer.class);
        AccessPolicy.require(count!=null && count>0);
        return scopedResume(user,courses.findById(id).orElseThrow());
    }
    /** Resume policy sees only the selected course's completion/score facts, never the learner's full history. */
    private LearnerWorkspaceService.Resume scopedResume(User actor,Course course) {
        var user=User.builder().id(actor.getId()).username(actor.getUsername()).role(actor.getRole()).roles(actor.getRoles())
            .status(actor.getStatus()).organizationUnit(actor.getOrganizationUnit()).scopeUnitIds(actor.getScopeUnitIds())
            .dynamicAccessInitialized(actor.isDynamicAccessInitialized()).build();
        if (actor.isDynamicAccessInitialized()) user.setPolicySnapshot(AccessPolicy.policies(actor));
        user.getRegisteredCourses().add(course);
        var params=Map.<String,Object>of("uid",actor.getId(),"cid",course.getId());
        jdbc.query("select cl.lesson_id from user_completed_lessons cl join lessons l on l.id=cl.lesson_id where cl.user_id=:uid and l.course_id=:cid",params,r->{user.getCompletedLessons().add(Lesson.builder().id(r.getString(1)).build());});
        jdbc.query("select lesson_id,score,total from user_quiz_scores where user_id=:uid and course_id=:cid",params,r->{
            String lesson=r.getString(1);user.getQuizScores().add(UserQuizScore.builder().course(course).lesson(lesson==null?null:Lesson.builder().id(lesson).build()).score(r.getInt(2)).total(r.getInt(3)).build());
        });
        return workspace.resumeCourse(course,user);
    }
    public LearnerDashboardQuery.Page page(LearnerDashboardQuery.Filter filter) { return query.page(auth.current(),filter); }
    public List<Map<String,String>> choices(String search,int size) { return query.courseChoices(auth.current(),search,size); }
}
