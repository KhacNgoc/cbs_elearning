package com.bidv.coretraining.learner.repository;
import com.bidv.coretraining.learner.dto.LearnerDashboardDto.*;
import com.bidv.coretraining.learner.repository.support.LearnerQueryScope;
import java.util.*;
public interface LearnerDashboardQueryRepository {
 Summary summary(LearnerQueryScope scope);
 Page page(LearnerQueryScope scope,Filter filter);
 /** Dashboard summary, filter choices for the tab and the course to resume, from one facts evaluation. */
 OverviewFacts overview(LearnerQueryScope scope,String tab);
 List<CourseChoice> courseChoices(LearnerQueryScope scope,String search,int size);
 List<CourseChoice> accessibleCourses(LearnerQueryScope scope,int limit);
 List<LearnableCourseRow> learnableCourses(LearnerQueryScope scope,boolean learner);
 boolean canResume(LearnerQueryScope scope,String id);
 ResumeFacts resumeFacts(Long userId,String courseId);
 record OverviewFacts(Summary summary,Map<String,List<Choice>> choices,String resumeCourseId) {}
 record LearnableCourseRow(String id,String name,boolean editor) {}
 record ResumeScore(String lessonId,int score,int total) {}
 record ResumeFacts(List<String> completedLessons,List<ResumeScore> scores) {}
}
