package com.bidv.coretraining.quiz.service.impl;
import com.bidv.coretraining.policy.LearningPolicy;


import com.bidv.coretraining.quiz.dto.LearnerQuizEntryDto.*;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.quiz.repository.LearnerQuizEntryRepository;
import com.bidv.coretraining.quiz.service.LearnerQuizEntryService;
import com.bidv.coretraining.user.repository.UserRepository;
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
  } else lessonId=null;
  var latest=entryScore(user,course,lesson,lessonId);
  Integer score=latest==null?null:latest.score();
  Integer total=latest==null?null:latest.total();
  return new Context(courseId,course.getName(),lessonId,lesson==null?course.getQuizPassingScore():lesson.getQuizPassingScore(),score,total,
   lesson==null||lesson.getQuiz()==null?null:lesson.getQuiz().getTimeLimitMinutes());
 }
 /**
  * A learner who may take the quiz gets its context. One who no longer may (course of a former unit, quiz locked
  * or withdrawn) still gets it when they have a result to review there, as the profile's result list offers.
  */
 private LearnerQuizEntryRepository.Score entryScore(com.bidv.coretraining.user.entity.User user,
   com.bidv.coretraining.course.entity.Course course,Lesson lesson,String lessonId) {
  try {
   if(lesson!=null) LearningPolicy.assertLessonAccess(user,lesson); else LearningPolicy.assertCourseAccess(user,course);
  } catch(org.springframework.security.access.AccessDeniedException denied) {
   LearningPolicy.assertResultAccess(user,course);
   return scores.latestScore(user.getId(),course.getId(),lessonId).orElseThrow(()->denied);
  }
  return scores.latestScore(user.getId(),course.getId(),lessonId).orElse(null);
 }
}
