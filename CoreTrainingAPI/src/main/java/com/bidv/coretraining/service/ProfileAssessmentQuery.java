package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.User;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class ProfileAssessmentQuery {
 private final NamedParameterJdbcTemplate jdbc;private final LearnerDashboardQuery dashboard;
 public ProfileAssessmentQuery(NamedParameterJdbcTemplate jdbc,LearnerDashboardQuery dashboard){this.jdbc=jdbc;this.dashboard=dashboard;}
 public record Group(LearnerWorkspaceService.Attempt latest,LearnerWorkspaceService.Attempt best,long attemptCount,String courseName){}
 public record Results(List<Group> items,long total,Integer bestScore,List<Map<String,Object>> legacyScores,long legacyTotal){}
 public record Awards(int points,long total,long completedLessons,long totalLessons,List<Map<String,Object>> items){}
 public Awards awards(User u,int page,int size){
  int boundedSize=Math.max(1,Math.min(size,100));long offset=(long)Math.max(0,Math.min(page,1000000))*boundedSize;
  var params=Map.<String,Object>of("uid",u.getId());
  long total=jdbc.queryForObject("select count(*) from user_badges where user_id=:uid",params,Long.class);
  var items=jdbc.query("select b.id,b.title,b.type,b.course_id,c.name course_name,ub.unlocked_at,b.icon from user_badges ub join badges b on b.id=ub.badge_id join courses c on c.id=b.course_id where ub.user_id=:uid order by ub.unlocked_at desc,ub.id desc limit "+boundedSize+" offset "+offset,params,(r,n)->{
   Map<String,Object> value=new HashMap<>();value.put("id",r.getString("id"));value.put("title",r.getString("title"));value.put("type",r.getString("type"));value.put("courseId",r.getString("course_id"));value.put("courseName",r.getString("course_name"));value.put("unlockedAt",r.getObject("unlocked_at",java.time.LocalDate.class));value.put("icon",r.getString("icon"));return value;
  });
  var summary=dashboard.summary(u);return new Awards(u.getPoints()==null?0:u.getPoints(),total,summary.completed(),summary.total(),items);
 }
 public record History(List<LearnerWorkspaceService.Attempt> items,String nextCursor,boolean hasMore){}
 private LearnerDashboardQuery.Context attempts(User u){var c=dashboard.visible(u);return new LearnerDashboardQuery.Context(c.sql()+"""
 , attempts_visible as (select a.id,a.quiz_id,a.revision,a.score,a.total,a.passing_score,a.passed,a.taken_at,q.title quiz_title,l.title lesson_title,l.id lesson_id,c.id course_id,c.name course_name,c.domain_id,c.domain_name,
 row_number() over(partition by a.quiz_id,a.revision order by a.taken_at desc,a.id desc) latest_row,
 row_number() over(partition by a.quiz_id,a.revision order by a.score*1.0/a.total desc,a.taken_at desc,a.id desc) best_row,
 count(*) over(partition by a.quiz_id,a.revision) attempt_count
 from quiz_attempts a join quizzes q on q.id=a.quiz_id join lessons l on l.id=q.lesson_id join visible c on c.id=l.course_id where a.user_id=:uid and a.total>0),
 grouped as (select a.id latest_id,b.id best_id,a.quiz_id,a.revision,a.taken_at,a.course_name,a.quiz_title,a.lesson_title from attempts_visible a join attempts_visible b on a.quiz_id=b.quiz_id and a.revision=b.revision and b.best_row=1 where a.latest_row=1),
 legacy as (select s.*,coalesce(l.title,c.name) title,c.name course_name from user_quiz_scores s join visible c on c.id=s.course_id left join lessons l on l.id=s.lesson_id where s.user_id=:uid and s.total>0 and not exists(select 1 from attempts_visible a where a.course_id=s.course_id and (a.lesson_id=s.lesson_id or (a.lesson_id is null and s.lesson_id is null))))
 """,new HashMap<>(c.params()));}
 public Results results(User u,int page,int size,String search){var c=attempts(u);var p=c.params();p.put("limit",Math.max(1,Math.min(100,size)));p.put("offset",(long)Math.max(0,Math.min(1000000,page))*(int)p.get("limit"));
 String where="";if(search!=null&&!search.isBlank()){p.put("search","%"+LearnerDashboardQuery.normalize(search.trim()).replace("!","!!").replace("%","!%").replace("_","!_")+"%");where=" where "+LearnerDashboardQuery.normalizedSql("coalesce(quiz_title,lesson_title) || ' ' || course_name")+" like :search escape '!'";}
 var groups=jdbc.query(c.sql()+"select a.*,b.id best_id,b.score best_score,b.total best_total,b.passing_score best_passing_score,b.passed best_passed,b.taken_at best_taken_at from (select * from grouped"+where+" order by taken_at desc,quiz_id,revision limit "+p.get("limit")+" offset "+p.get("offset")+") g join attempts_visible a on a.id=g.latest_id join attempts_visible b on b.id=g.best_id order by a.taken_at desc,a.quiz_id,a.revision",p,(r,n)->{
 var latest=attempt(r);var best=new LearnerWorkspaceService.Attempt(r.getString("best_id"),latest.quizId(),latest.courseId(),latest.lessonId(),latest.title(),latest.domainId(),latest.domainName(),latest.revision(),r.getInt("best_score"),r.getInt("best_total"),r.getInt("best_passing_score"),r.getBoolean("best_passed"),r.getObject("best_taken_at",LocalDateTime.class));return new Group(latest,best,r.getLong("attempt_count"),r.getString("course_name"));});
 long total=jdbc.queryForObject(c.sql()+"select count(*) from grouped"+where,p,Long.class);
 Double best=jdbc.queryForObject(c.sql()+"select max(score_value) from (select score*100.0/total score_value from attempts_visible union all select score*100.0/total score_value from legacy) scores",p,Double.class);
 var legacy=jdbc.query(c.sql()+"select * from legacy order by taken_at desc,id desc limit "+p.get("limit")+" offset "+p.get("offset")+"",p,(r,n)->{Map<String,Object> row=new HashMap<>();row.put("courseId",r.getString("course_id"));row.put("lessonId",r.getString("lesson_id"));row.put("title",r.getString("title"));row.put("courseName",r.getString("course_name"));row.put("score",r.getInt("score"));row.put("total",r.getInt("total"));row.put("takenAt",r.getObject("taken_at",LocalDateTime.class));return row;});
 return new Results(groups,total,best==null?null:(int)Math.round(best),legacy,jdbc.queryForObject(c.sql()+"select count(*) from legacy",p,Long.class));
 }
 public History history(User u,String quiz,int revision,String cursor,int size){var c=dashboard.visible(u);var p=c.params();p.put("quiz",quiz);p.put("revision",revision);int limit=Math.max(1,Math.min(100,size));p.put("limit",limit+1);String before="";
 if(cursor!=null&&!cursor.isBlank()){try{String[] parts=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);if(parts.length!=4||!parts[0].equals(quiz)||!parts[1].equals(String.valueOf(revision)))throw new IllegalArgumentException();p.put("time",LocalDateTime.parse(parts[2]));p.put("id",parts[3]);before=" and (taken_at<:time or (taken_at=:time and id<:id))";}catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid history cursor");}}
 var rows=jdbc.query(c.sql()+"select a.id,a.quiz_id,a.revision,a.score,a.total,a.passing_score,a.passed,a.taken_at,q.title quiz_title,l.title lesson_title,l.id lesson_id,c.id course_id,c.name course_name,c.domain_id,c.domain_name from quiz_attempts a join quizzes q on q.id=a.quiz_id join lessons l on l.id=q.lesson_id join visible c on c.id=l.course_id where a.user_id=:uid and a.total>0 and a.quiz_id=:quiz and a.revision=:revision"+before.replace("taken_at","a.taken_at").replace("id<:id","a.id<:id")+" order by a.taken_at desc,a.id desc limit "+(limit+1),p,(r,n)->attempt(r));boolean more=rows.size()>limit;var items=rows.subList(0,Math.min(limit,rows.size()));var last=items.isEmpty()?null:items.get(items.size()-1);String next=more?Base64.getUrlEncoder().withoutPadding().encodeToString((quiz+"|"+revision+"|"+last.takenAt()+"|"+last.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null;return new History(items,next,more);
 }
 public Map<String,Object> stats(User u){var s=dashboard.summary(u);Map<String,Object> result=new HashMap<>();result.put("joined",s.joined());result.put("completed",s.completed());result.put("total",s.total());result.put("percent",s.percent());result.put("notStarted",s.notStarted());result.put("inProgress",s.inProgress());result.put("complete",s.complete());result.put("mandatory",s.mandatory());result.put("mandatoryComplete",s.mandatoryComplete());
 var a=attempts(u);var f=dashboard.facts(u);String sql=f.sql()+a.sql().substring(a.sql().indexOf(", attempts_visible"))+"""
 , evaluated as (select a.*,row_number() over(partition by quiz_id order by taken_at desc,id desc) quiz_row from attempts_visible a where latest_row=1), domains as (select distinct domain_id,domain_name from facts where source in ('ASSIGNED','REGISTERED'))
 select d.domain_id,d.domain_name,count(b.id) score_count,avg(b.score*100.0/b.total) score_value from domains d left join evaluated e on e.domain_id=d.domain_id and e.quiz_row=1 left join attempts_visible b on b.quiz_id=e.quiz_id and b.revision=e.revision and b.best_row=1 group by d.domain_id,d.domain_name order by d.domain_id
 """;
 result.put("competencies",jdbc.query(sql,f.params(),(r,n)->{Map<String,Object> v=new HashMap<>();v.put("id",r.getString(1));v.put("label",r.getString(2));v.put("count",r.getLong(3));java.math.BigDecimal score=r.getBigDecimal(4);v.put("value",score==null?null:Math.round(score.doubleValue()));return v;}));return result;
 }
 private LearnerWorkspaceService.Attempt attempt(java.sql.ResultSet r)throws java.sql.SQLException{return new LearnerWorkspaceService.Attempt(r.getString("id"),r.getString("quiz_id"),r.getString("course_id"),r.getString("lesson_id"),r.getString("quiz_title")==null?r.getString("lesson_title"):r.getString("quiz_title"),r.getString("domain_id"),r.getString("domain_name"),r.getInt("revision"),r.getInt("score"),r.getInt("total"),r.getInt("passing_score"),r.getBoolean("passed"),r.getObject("taken_at",LocalDateTime.class));}
}
