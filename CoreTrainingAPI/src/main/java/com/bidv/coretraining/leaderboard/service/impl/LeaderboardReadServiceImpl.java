package com.bidv.coretraining.leaderboard.service.impl;


import com.bidv.coretraining.leaderboard.dto.LeaderboardReadDto.*;

import com.bidv.coretraining.leaderboard.repository.LeaderboardQueryRepository;
import com.bidv.coretraining.leaderboard.service.LeaderboardReadService;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LeaderboardReadServiceImpl implements LeaderboardReadService {

 private final LeaderboardQueryRepository queries;
 public LeaderboardReadServiceImpl(LeaderboardQueryRepository queries) { this.queries=queries; }
 public Page page(User actor,int page,int size) {
  int boundedSize=Math.max(1,Math.min(100,size)); int boundedPage=Math.max(0,Math.min(1000000,page));
  var scope=scope(actor);
  return queries.page(actor.getId(),scope,boundedPage,boundedSize);
 }
 private LeaderboardQueryRepository.Scope scope(User actor) {
  if(AccessPolicy.system(actor)) return new LeaderboardQueryRepository.Scope(true,false,Set.of());
  if(!AccessPolicy.permissions(actor).contains("overview:read"))
   return new LeaderboardQueryRepository.Scope(false,actor.getOrganizationUnit()==null,
     actor.getOrganizationUnit()==null?Set.of():Set.of(actor.getOrganizationUnit().getId()));
  Set<String> roots=new HashSet<>();
  if(actor.isDynamicAccessInitialized()) AccessPolicy.policies(actor).stream().filter(r->r.active()&&!r.learnerRole()&&r.permissions().contains("overview:read")).forEach(r->roots.addAll(r.unitIds()));
  else {roots.addAll(actor.getScopeUnitIds());if(roots.isEmpty()&&actor.getOrganizationUnit()!=null)roots.add(actor.getOrganizationUnit().getId());}
  Map<String,LeaderboardQueryRepository.Unit> units=new HashMap<>(); queries.units().forEach(u->units.put(u.id(),u));
  Set<String> allowed=new HashSet<>();for(String id:units.keySet()){Set<String> seen=new HashSet<>();String node=id;boolean active=true,matched=false;while(node!=null){var unit=units.get(node);if(unit==null||!unit.active()||!seen.add(node)){active=false;break;}if(roots.contains(node))matched=true;node=unit.parent();}if(active&&matched)allowed.add(id);}
  return new LeaderboardQueryRepository.Scope(false,false,allowed);
 }
}
