package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LeaderboardUserDto;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LeaderboardReadService {
 public record Page(List<LeaderboardUserDto> items, long total, LeaderboardUserDto currentUser) {}
 private final NamedParameterJdbcTemplate jdbc;
 public LeaderboardReadService(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
 public Page page(User actor,int page,int size) {
  int boundedSize=Math.max(1,Math.min(100,size)); int boundedPage=Math.max(0,Math.min(1000000,page));
  Map<String,Object> params=new HashMap<>(); params.put("actor",actor.getId());params.put("size",boundedSize);params.put("offset",(long)boundedPage*boundedSize);
  String scope="1=1";
  if(!AccessPolicy.system(actor)) {
   if(!AccessPolicy.permissions(actor).contains("overview:read")) {
    if(actor.getOrganizationUnit()==null) scope="u.unit_id is null";
    else {scope="u.unit_id=:unit";params.put("unit",actor.getOrganizationUnit().getId());}
   } else {
    Set<String> roots=new HashSet<>();
    if(actor.isDynamicAccessInitialized()) AccessPolicy.policies(actor).stream().filter(r->r.active()&&!r.learnerRole()&&r.permissions().contains("overview:read")).forEach(r->roots.addAll(r.unitIds()));
    else {roots.addAll(actor.getScopeUnitIds());if(roots.isEmpty()&&actor.getOrganizationUnit()!=null)roots.add(actor.getOrganizationUnit().getId());}
    record Unit(String parent,boolean active) {}
    Map<String,Unit> units=new HashMap<>();jdbc.query("select id,parent_id,active from organization_units",Map.of(),r->{units.put(r.getString(1),new Unit(r.getString(2),r.getBoolean(3)));});
    Set<String> allowed=new HashSet<>();for(String id:units.keySet()){Set<String> seen=new HashSet<>();String node=id;boolean active=true,matched=false;while(node!=null){var unit=units.get(node);if(unit==null||!unit.active()||!seen.add(node)){active=false;break;}if(roots.contains(node))matched=true;node=unit.parent();}if(active&&matched)allowed.add(id);}
    params.put("units",allowed.isEmpty()?Set.of("__no_scope__"):allowed);
    scope="u.unit_id in (:units)";
   }
  }
  String eligible="select u.id,u.username,u.full_name,u.avatar_color,u.points from users u where u.status='ACTIVE' and u.role <> 'ROLE_ADMIN' and "+scope;
  String ranked="(select e.*,row_number() over(order by points desc,id asc) as ranking from ("+eligible+") e) r";
  String columns="select r.*, (select count(*) from user_badges b where b.user_id=r.id) as badge_count,(select count(*) from user_completed_lessons l where l.user_id=r.id) as lesson_count from "+ranked+" ";
  var items=jdbc.query(columns+"order by ranking limit "+boundedSize+" offset "+((long)boundedPage*boundedSize),params,(rs,n)->dto(rs));
  long total=Objects.requireNonNull(jdbc.queryForObject("select count(*) from ("+eligible+") eligible",params,Long.class));
  var current=jdbc.query(columns+"where r.id=:actor",params,(rs,n)->dto(rs));
  return new Page(items,total,current.isEmpty()?null:current.get(0));
 }
 private LeaderboardUserDto dto(java.sql.ResultSet rs)throws java.sql.SQLException {
  return LeaderboardUserDto.builder().rank(rs.getInt("ranking")).username(rs.getString("username")).fullName(rs.getString("full_name")).avatarColor(rs.getString("avatar_color")).points(rs.getInt("points")).badgesCount(rs.getInt("badge_count")).completedLessonsCount(rs.getInt("lesson_count")).build();
 }
}
