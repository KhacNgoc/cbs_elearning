package com.bidv.coretraining.course.repository.impl;

import com.bidv.coretraining.course.dto.CourseRatingDto;
import com.bidv.coretraining.course.repository.CourseRatingReportRepository;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository.Scope;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class CourseRatingReportRepositoryImpl implements CourseRatingReportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CourseRatingReportRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A predicate on the learner {@code u} (a row of users) of a fact about course {@code course}: the learner is
     * in a unit of one of the scope's roles, and an assigned-courses-only role also needs the course assigned.
     */
    private static String learners(Scope scope, String course, Map<String, Object> p) {
        if (scope.system())
            return "1=1";
        List<String> parts = new ArrayList<>();
        int index = 0;
        for (var role : scope.roles()) {
            if (role.unitIds().isEmpty())
                continue;
            String name = "learnerUnits" + (index++);
            p.put(name, role.unitIds());
            String part = "u.unit_id in (:" + name + ")";
            if (role.assignedCoursesOnly()) {
                p.put("actor", scope.actorId());
                part += " and exists(select 1 from course_instructors ci where ci.course_id=" + course + " and ci.user_id=:actor)";
            }
            parts.add("(" + part + ")");
        }
        return parts.isEmpty() ? "1=0" : "(" + String.join(" or ", parts) + ")";
    }

    public List<CourseName> courses(Scope scope, Map<Integer, Set<String>> grantUnits) {
        Map<String, Object> p = new HashMap<>();
        String allocated;
        if (scope.system())
            allocated = "c.status='PUBLISHED'";
        else {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < scope.roles().size(); i++) {
                Set<String> units = grantUnits.getOrDefault(i, Set.of());
                if (units.isEmpty())
                    continue;
                p.put("grantUnits" + i, units);
                String part = "exists(select 1 from course_unit_grants g where g.course_id=c.id and g.unit_id in (:grantUnits" + i + "))";
                if (scope.roles().get(i).assignedCoursesOnly()) {
                    p.put("actor", scope.actorId());
                    part += " and exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=:actor)";
                }
                parts.add("(" + part + ")");
            }
            allocated = parts.isEmpty() ? "1=0" : "c.status='PUBLISHED' and (" + String.join(" or ", parts) + ")";
        }
        String scoped = learners(scope, "c.id", p);
        String sql = "select c.id,c.name from courses c where (" + allocated + ")"
                + " or exists(select 1 from course_ratings r join users u on u.id=r.user_id where r.course_id=c.id and " + scoped + ")"
                + " or exists(select 1 from user_completed_courses cc join users u on u.id=cc.user_id where cc.course_id=c.id and " + scoped + ")"
                + " order by c.name,c.id";
        return jdbc.query(sql, p, (rs, i) -> new CourseName(rs.getString(1), rs.getString(2)));
    }

    public Map<String, Figures> aggregate(Scope scope, Collection<String> courseIds) {
        Map<String, Figures> result = new HashMap<>();
        if (courseIds.isEmpty())
            return result;
        Map<String, Object> p = new HashMap<>(Map.of("ids", courseIds));
        String sql = "select r.course_id,count(*) n,avg(cast(r.stars as double precision)) average,sum(case when r.stars<=2 then 1 else 0 end) low"
                + " from course_ratings r join users u on u.id=r.user_id where r.course_id in (:ids) and " + learners(scope, "r.course_id", p)
                + " group by r.course_id";
        jdbc.query(sql, p, rs -> {
            result.put(rs.getString(1), new Figures(rs.getLong(2), rs.getDouble(3), rs.getLong(4)));
        });
        return result;
    }

    public Map<String, Long> completed(Scope scope, Collection<String> courseIds) {
        Map<String, Long> result = new HashMap<>();
        if (courseIds.isEmpty())
            return result;
        Map<String, Object> p = new HashMap<>(Map.of("ids", courseIds));
        String sql = "select cc.course_id,count(*) from user_completed_courses cc join users u on u.id=cc.user_id"
                + " where cc.course_id in (:ids) and " + learners(scope, "cc.course_id", p) + " group by cc.course_id";
        jdbc.query(sql, p, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }

    public Map<Integer, Long> distribution(Scope scope, Collection<String> courseIds) {
        Map<Integer, Long> result = new HashMap<>();
        if (courseIds.isEmpty())
            return result;
        Map<String, Object> p = new HashMap<>(Map.of("ids", courseIds));
        String sql = "select r.stars,count(*) from course_ratings r join users u on u.id=r.user_id where r.course_id in (:ids) and "
                + learners(scope, "r.course_id", p) + " group by r.stars";
        jdbc.query(sql, p, rs -> {
            result.put(rs.getInt(1), rs.getLong(2));
        });
        return result;
    }

    private static String feedbackWhere(Scope scope, boolean lowOnly, Map<String, Object> p) {
        return " from course_ratings r join users u on u.id=r.user_id join courses c on c.id=r.course_id where r.course_id in (:ids) and "
                + learners(scope, "r.course_id", p) + (lowOnly ? " and r.stars<=2" : "");
    }

    public List<CourseRatingDto.Feedback> feedback(Scope scope, Collection<String> courseIds, boolean lowOnly, int page, int size) {
        if (courseIds.isEmpty())
            return List.of();
        Map<String, Object> p = new HashMap<>(Map.of("ids", courseIds));
        String sql = "select r.id,r.course_id,c.name,u.full_name,r.stars,r.content,r.updated_at" + feedbackWhere(scope, lowOnly, p)
                + " order by r.updated_at desc,r.id desc limit " + size + " offset " + ((long) page * size);
        return jdbc.query(sql, p, (rs, i) -> new CourseRatingDto.Feedback(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6),
                rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toLocalDateTime()));
    }

    public long feedbackCount(Scope scope, Collection<String> courseIds, boolean lowOnly) {
        if (courseIds.isEmpty())
            return 0;
        Map<String, Object> p = new HashMap<>(Map.of("ids", courseIds));
        Long n = jdbc.queryForObject("select count(*)" + feedbackWhere(scope, lowOnly, p), p, Long.class);
        return n == null ? 0 : n;
    }
}
