package com.bidv.coretraining.repository;
import com.bidv.coretraining.repository.support.LearnerQueryScope;
import com.bidv.coretraining.dto.LearnerDashboardDto.*;
import java.util.*;
public interface LearnerDashboardQueryRepository {
 Summary summary(LearnerQueryScope scope);
 Page page(LearnerQueryScope scope,Filter filter);
 Map<String,List<Choice>> choices(LearnerQueryScope scope,String tab);
 String resumeCourseId(LearnerQueryScope scope);
 List<CourseChoice> courseChoices(LearnerQueryScope scope,String search,int size);
 List<CourseChoice> accessibleCourses(LearnerQueryScope scope,int limit);
 List<LearnableCourseRow> learnableCourses(LearnerQueryScope scope,boolean learner);
 boolean canResume(LearnerQueryScope scope,String id);
 ResumeFacts resumeFacts(Long userId,String courseId);
 record LearnableCourseRow(String id,String name,boolean editor) {}
 record ResumeScore(String lessonId,int score,int total) {}
 record ResumeFacts(List<String> completedLessons,List<ResumeScore> scores) {}
}
