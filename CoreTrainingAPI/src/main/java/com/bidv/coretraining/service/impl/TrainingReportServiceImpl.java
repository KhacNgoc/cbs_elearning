package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.policy.LearningPolicy;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.TrainingReportDto.*;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Read-only snapshot of current assignments and persisted learning facts. No seed/fallback data. */
@Service
@Transactional(readOnly = true)
public class TrainingReportServiceImpl implements TrainingReportService {

    private final AuthorizationService auth;
    private final UserRepository users;
    private final CourseRepository courses;
    private final LessonProgressRepository progress;
    public TrainingReportServiceImpl(AuthorizationService auth, UserRepository users, CourseRepository courses, LessonProgressRepository progress) {
        this.auth=auth; this.users=users; this.courses=courses; this.progress=progress;
    }
    public Snapshot snapshot() {
        auth.requireOperation("overview", "read");
        User actor=auth.current();
        var catalog=courses.findAll().stream().filter(c->"PUBLISHED".equals(c.getStatus())).toList();
        List<Row> rows=new ArrayList<>();
        for(User learner:users.findAll()) {
            if(!AccessPolicy.allows(actor,"overview:read",learner.getOrganizationUnit())) continue;
            var history=progress.findByUserId(learner.getId());
            for(Course course:catalog) {
                if(actor.isDynamicAccessInitialized() && !AccessPolicy.system(actor) && AccessPolicy.policies(actor).stream().noneMatch(r->r.active()
                        && r.permissions().contains("overview:read") && AccessPolicy.roleScope(r,learner.getOrganizationUnit())
                        && (!r.assignedCoursesOnly()||course.getInstructorIds().contains(actor.getId())))) continue;
                boolean mandatory=AccessPolicy.mandatory(learner,course) || learner.hasGroupMandatoryCourse(course.getId());
                boolean registered=learner.getRegisteredCourses().stream().anyMatch(c->c.getId().equals(course.getId()));
                if(!mandatory&&!registered) continue;
                var lessons=LearningPolicy.publishedChapters(course).stream().flatMap(c->LearningPolicy.publishedLessons(c).stream()).toList();
                var lessonIds=lessons.stream().map(Lesson::getId).collect(java.util.stream.Collectors.toSet());
                int done=(int)lessons.stream().filter(l->LearningPolicy.completed(learner,l)).count();
                boolean complete=LearningPolicy.courseCompleted(learner,course);
                var scores=learner.getQuizScores().stream().filter(q->q.getCourse().getId().equals(course.getId()) && q.getTotal()>0 && (q.getLesson()==null||lessonIds.contains(q.getLesson().getId())))
                        .sorted(Comparator.comparing(UserQuizScore::getTakenAt).thenComparing(UserQuizScore::getId)).toList();
                Map<String,List<UserQuizScore>> exams=new LinkedHashMap<>();
                scores.forEach(q->exams.computeIfAbsent(q.getLesson()==null?"course":q.getLesson().getId(),k->new ArrayList<>()).add(q));
                var attempts=scores.stream().map(q->new Attempt(percent(q),q.getTakenAt(),threshold(q),q.getLesson()==null?course.getName():q.getLesson().getTitle())).toList();
                Double average=exams.isEmpty()?null:exams.values().stream().mapToDouble(list->percent(list.get(list.size()-1))).average().orElseThrow();
                boolean allAttemptedPassed=!exams.isEmpty()&&exams.values().stream().allMatch(list->list.stream().anyMatch(q->Math.round(percent(q))>=threshold(q)));
                boolean requiredPassed=lessons.stream().filter(Lesson::isQuizRequired).allMatch(l->LearningPolicy.quizPassed(learner,l));
                boolean firstPassed=!exams.isEmpty()&&requiredPassed&&exams.values().stream().allMatch(list->Math.round(percent(list.get(0)))>=threshold(list.get(0)));
                boolean started=done>0||!scores.isEmpty()||history.stream().anyMatch(p->lessonIds.contains(p.getLesson().getId()));
                var completedAt=complete?learner.getCompletedCourses().get(course.getId()):null;
                rows.add(new Row(learner.getId()+":"+course.getId(),String.valueOf(learner.getId()),learner.getFullName(),
                        learner.getOrganizationUnit()==null?"Chưa có đơn vị":learner.getOrganizationUnit().getName(),
                        learner.getUserGroups().isEmpty()?"Chưa có nhóm":learner.groupNames(),course.getId(),course.getName(),
                        course.getProgram().getName(),course.getProgram().getDomain().getName(),
                        lessons.isEmpty()?0:(int)Math.round(done*100.0/lessons.size()),mandatory,
                        learner.getCourseDeadlines().getOrDefault(course.getId(),course.getCompletionDeadline()),completedAt==null?null:completedAt.toLocalDate(),
                        complete,started,attempts,average,allAttemptedPassed&&requiredPassed,firstPassed,course.getQuizPassingScore()));
            }
        }
        return new Snapshot(LocalDate.now(),rows);
    }
    private static double percent(UserQuizScore q) { return q.getScore()*100.0/q.getTotal(); }
    private static int threshold(UserQuizScore q) { return q.getLesson()==null?q.getCourse().getQuizPassingScore():q.getLesson().getQuizPassingScore(); }
}
