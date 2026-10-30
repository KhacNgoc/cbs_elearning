package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardReadServiceTest {
 @Test void filtersBeforePagingAndRanksTiesById() {
  var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:leaderboard;DB_CLOSE_DELAY=-1", "sa", ""));
  jdbc.execute("create table organization_units(id varchar primary key,parent_id varchar,active boolean)");
  jdbc.execute("create table users(id bigint primary key,username varchar,full_name varchar,avatar_color varchar,points int,status varchar,role varchar,unit_id varchar)");
  jdbc.execute("create table user_badges(user_id bigint)"); jdbc.execute("create table user_completed_lessons(user_id bigint)");
  jdbc.update("insert into organization_units values('a',null,true),('b',null,true)");
  jdbc.update("insert into users values(1,'hidden','Hidden',null,1000,'ACTIVE','ROLE_USER','b'),(2,'inactive','Inactive',null,900,'INACTIVE','ROLE_USER','a'),(3,'admin','Admin',null,800,'ACTIVE','ROLE_ADMIN','a'),(4,'first','First',null,100,'ACTIVE','ROLE_USER','a'),(5,'second','Second',null,100,'ACTIVE','ROLE_USER','a')");
  var actor = User.builder().id(5L).role("ROLE_USER").organizationUnit(com.bidv.coretraining.entity.OrganizationUnit.builder().id("a").build()).build();
  var service = new LeaderboardReadService(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
  var page = service.page(actor, 0, 1);
  assertThat(page.total()).isEqualTo(2); assertThat(page.items()).hasSize(1);
  assertThat(page.items().get(0).getUsername()).isEqualTo("first");
  assertThat(page.currentUser().getRank()).isEqualTo(2);
  assertThat(service.page(actor,1,1).items().get(0).getUsername()).isEqualTo("second");
  jdbc.update("insert into organization_units values('child','a',true),('disabled','a',false),('disabled-child','disabled',true)");
  jdbc.update("insert into users values(6,'child','Child',null,200,'ACTIVE','ROLE_USER','child'),(7,'disabled','Disabled',null,1000,'ACTIVE','ROLE_USER','disabled-child')");
  actor.setDynamicAccessInitialized(true);
  actor.setPolicySnapshot(java.util.List.of(new com.bidv.coretraining.security.RolePolicy("manager","MANAGER","Manager",true,false,false,false,false,false,java.util.Set.of("overview:read"),java.util.Set.of("a")),new com.bidv.coretraining.security.RolePolicy("learner","LEARNER","Learner",true,false,true,true,false,false,java.util.Set.of("overview:read"),java.util.Set.of("b"))));
  var scoped=service.page(actor,0,1);assertThat(scoped.total()).isEqualTo(3);assertThat(scoped.items()).extracting(x->x.getUsername()).containsExactly("child");assertThat(scoped.currentUser().getRank()).isEqualTo(3);
 }
}
