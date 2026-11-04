package com.bidv.coretraining.repository.impl;

import com.bidv.coretraining.repository.LeaderboardQueryRepository;
import com.bidv.coretraining.dto.LeaderboardReadDto.Page;
import com.bidv.coretraining.dto.LeaderboardUserDto;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class LeaderboardQueryRepositoryImpl implements LeaderboardQueryRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public LeaderboardQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Unit> units() {
    return jdbc.query("select id,parent_id,active from organization_units", Map.of(),
        (r, n) -> new Unit(r.getString(1), r.getString(2), r.getBoolean(3)));
  }

  @Override
  public Page page(Long actorId, Scope authorizedScope, int page, int size) {
    int boundedSize = size, boundedPage = page;
    Map<String, Object> params = new HashMap<>();
    params.put("actor", actorId);
    params.put("size", size);
    params.put("offset", (long) page * size);
    String scope;
    if (authorizedScope.unrestricted())
      scope = "1=1";
    else if (authorizedScope.withoutUnit())
      scope = "u.unit_id is null";
    else {
      params.put("units", authorizedScope.unitIds().isEmpty() ? Set.of("__no_scope__") : authorizedScope.unitIds());
      scope = "u.unit_id in (:units)";
    }
    String eligible = "select u.id,u.username,u.full_name,u.avatar_color,u.points from users u where u.status='ACTIVE' and u.role <> 'ROLE_ADMIN' and "
        + scope;
    String ranked = "(select e.*,row_number() over(order by points desc,id asc) as ranking from (" + eligible
        + ") e) r";
    String columns = "select r.*, (select count(*) from user_badges b where b.user_id=r.id) as badge_count,(select count(*) from user_completed_lessons l where l.user_id=r.id) as lesson_count from "
        + ranked + " ";
    var items = jdbc.query(
        columns + "order by ranking limit " + boundedSize + " offset " + ((long) boundedPage * boundedSize), params,
        (rs, n) -> dto(rs));
    long total = Objects
        .requireNonNull(jdbc.queryForObject("select count(*) from (" + eligible + ") eligible", params, Long.class));
    var current = jdbc.query(columns + "where r.id=:actor", params, (rs, n) -> dto(rs));
    return new Page(items, total, current.isEmpty() ? null : current.get(0));
  }

  private LeaderboardUserDto dto(java.sql.ResultSet rs) throws java.sql.SQLException {
    return LeaderboardUserDto.builder().rank(rs.getInt("ranking")).username(rs.getString("username"))
        .fullName(rs.getString("full_name")).avatarColor(rs.getString("avatar_color")).points(rs.getInt("points"))
        .badgesCount(rs.getInt("badge_count")).completedLessonsCount(rs.getInt("lesson_count")).build();
  }
}
