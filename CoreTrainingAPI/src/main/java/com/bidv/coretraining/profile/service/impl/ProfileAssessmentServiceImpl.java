package com.bidv.coretraining.profile.service.impl;
import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.learner.repository.LearnerDashboardQueryRepository;
import com.bidv.coretraining.learner.repository.support.LearnerQueryScopeFactory;
import com.bidv.coretraining.profile.dto.ProfileAssessmentDto.*;
import com.bidv.coretraining.profile.repository.ProfileAssessmentQueryRepository;
import com.bidv.coretraining.profile.service.ProfileAssessmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.LocalDateTime;
@Service
@Transactional(readOnly=true)
public class ProfileAssessmentServiceImpl implements ProfileAssessmentService {
 private final AuthorizationService auth;
 private final ProfileAssessmentQueryRepository query;
 private final LearnerDashboardQueryRepository dashboard;
 public ProfileAssessmentServiceImpl(AuthorizationService auth,ProfileAssessmentQueryRepository query,LearnerDashboardQueryRepository dashboard){this.auth=auth;this.query=query;this.dashboard=dashboard;}
 public Awards awards(int page,int size){
  var user=auth.current();var inventory=query.awards(user.getId(),page,size);var summary=dashboard.summary(LearnerQueryScopeFactory.from(user));
  return new Awards(user.getPoints()==null?0:user.getPoints(),inventory.total(),summary.completed(),summary.total(),inventory.items());
 }
 public Results results(int page,int size,String search){return query.results(LearnerQueryScopeFactory.from(auth.current()),page,size,search);}
 public History history(String quiz,int revision,String cursor,int size){
  var scope=LearnerQueryScopeFactory.from(auth.current());LocalDateTime time=null;String id=null;
  if(cursor!=null && !cursor.isBlank()){
   try{
    String[] parts=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);
    if(parts.length!=4 || !parts[0].equals(quiz) || !parts[1].equals(String.valueOf(revision))) throw new IllegalArgumentException();
    time=LocalDateTime.parse(parts[2]);id=parts[3];
   }catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid history cursor");}
  }
  int limit=Math.max(1,Math.min(100,size));var rows=query.history(scope,quiz,revision,time,id,limit+1);
  boolean more=rows.size()>limit;var items=rows.subList(0,Math.min(limit,rows.size()));var last=items.isEmpty()?null:items.get(items.size()-1);
  String next=more?Base64.getUrlEncoder().withoutPadding().encodeToString((quiz+"|"+revision+"|"+last.takenAt()+"|"+last.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null;
  return new History(items,next,more);
 }
 public Stats stats(){
  var scope=LearnerQueryScopeFactory.from(auth.current());var s=dashboard.summary(scope);
  return new Stats(s.joined(),s.completed(),s.total(),s.percent(),s.notStarted(),s.inProgress(),s.complete(),s.mandatory(),s.mandatoryComplete(),query.competencies(scope));
 }
}
