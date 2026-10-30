package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.repository.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class LearnerQuizEntryService {
 public record Context(String id,String name,String lessonId,Integer quizPassingScore,Integer score,Integer total) {}
 private final AuthorizationService auth;private final UserRepository users;private final CourseRepository courses;
 private final LessonRepository lessons;private final NamedParameterJdbcTemplate jdbc;
 public LearnerQuizEntryService(AuthorizationService auth,UserRepository users,CourseRepository courses,LessonRepository lessons,NamedParameterJdbcTemplate jdbc) {
  this.auth=auth;this.users=users;this.courses=courses;this.lessons=lessons;this.jdbc=jdbc;
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
  var params=new HashMap<String,Object>();params.put("uid",user.getId());params.put("cid",courseId);params.put("lid",lessonId);
  var scores=jdbc.queryForList("select score,total from user_quiz_scores where user_id=:uid and course_id=:cid and "+(lessonId==null?"lesson_id is null":"lesson_id=:lid")+" order by taken_at desc,id desc limit 1",params);
  Integer score=scores.isEmpty()?null:((Number)scores.get(0).get("score")).intValue();
  Integer total=scores.isEmpty()?null:((Number)scores.get(0).get("total")).intValue();
  return new Context(courseId,course.getName(),lessonId,lesson==null?course.getQuizPassingScore():lesson.getQuizPassingScore(),score,total);
 }
}
