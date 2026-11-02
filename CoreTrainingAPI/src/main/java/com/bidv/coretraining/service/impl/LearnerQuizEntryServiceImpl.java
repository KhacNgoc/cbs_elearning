package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.policy.LearningPolicy;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.LearnerQuizEntryDto.*;

import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class LearnerQuizEntryServiceImpl implements LearnerQuizEntryService {

 private final AuthorizationService auth;private final UserRepository users;private final CourseRepository courses;
 private final LessonRepository lessons;private final LearnerQuizEntryRepository scores;
 public LearnerQuizEntryServiceImpl(AuthorizationService auth,UserRepository users,CourseRepository courses,LessonRepository lessons,LearnerQuizEntryRepository scores) {
  this.auth=auth;this.users=users;this.courses=courses;this.lessons=lessons;this.scores=scores;
 }
 public Context context(String courseId,String lessonId) {
  var user=users.findById(auth.current().getId()).orElseThrow(()->new ResourceNotFoundException("User"));
  var course=courses.findById(courseId).orElseThrow(()->new ResourceNotFoundException("Course"));
  Lesson lesson=null;
  if(lessonId!=null&&!lessonId.isBlank()) {
   lesson=lessons.findById(lessonId).orElseThrow(()->new ResourceNotFoundException("Lesson"));
   if(!courseId.equals(lesson.getCourse().getId())) throw new BadRequestException("Bài học không thuộc khóa học đã chọn");
   LearningPolicy.assertLessonAccess(user,lesson);
  } else {lessonId=null;LearningPolicy.assertCourseAccess(user,course);}
  var latest=scores.latestScore(user.getId(),courseId,lessonId).orElse(null);
  Integer score=latest==null?null:latest.score();
  Integer total=latest==null?null:latest.total();
  return new Context(courseId,course.getName(),lessonId,lesson==null?course.getQuizPassingScore():lesson.getQuizPassingScore(),score,total);
 }
}
