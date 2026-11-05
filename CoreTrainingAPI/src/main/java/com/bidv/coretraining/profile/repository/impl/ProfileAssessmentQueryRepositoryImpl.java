package com.bidv.coretraining.profile.repository.impl;
import com.bidv.coretraining.learner.dto.LearnerWorkspaceDto;
import com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations;
import com.bidv.coretraining.learner.repository.support.LearnerQueryScope;
import com.bidv.coretraining.profile.dto.ProfileAssessmentDto.*;
import com.bidv.coretraining.profile.repository.ProfileAssessmentQueryRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.LocalDateTime;
@Repository
public class ProfileAssessmentQueryRepositoryImpl implements ProfileAssessmentQueryRepository {
 private final NamedParameterJdbcTemplate jdbc;
 private final LearnerDashboardSqlRelations relations;
 public ProfileAssessmentQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc,LearnerDashboardSqlRelations relations){this.jdbc=jdbc;this.relations=relations;}
    public AwardsInventory awards(Long userId, int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        long offset = (long) Math.max(0, Math.min(page, 1000000)) * boundedSize;
        var params = Map.<String, Object>of("uid", userId);
        long total = jdbc.queryForObject("select count(*) from user_badges where user_id=:uid", params, Long.class);
        var items = jdbc.query(
                "select b.id,b.title,b.type,b.course_id,c.name course_name,ub.unlocked_at,b.icon from user_badges ub join badges b on b.id=ub.badge_id join courses c on c.id=b.course_id where ub.user_id=:uid order by ub.unlocked_at desc,ub.id desc limit "
                        + boundedSize + " offset " + offset,
                params, (r, n) -> new Award(r.getString("id"), r.getString("title"), r.getString("type"),
                        r.getString("course_id"), r.getString("course_name"),
                        r.getObject("unlocked_at", java.time.LocalDate.class), r.getString("icon")));
        return new AwardsInventory(total, items);
    }

    private LearnerDashboardSqlRelations.Context attempts(LearnerQueryScope u) {
        var c = relations.visible(u);
        return new LearnerDashboardSqlRelations.Context(
                c.sql() + """
                        , attempts_visible as (select a.id,a.quiz_id,a.revision,a.score,a.total,a.passing_score,a.passed,a.taken_at,q.title quiz_title,l.title lesson_title,l.id lesson_id,c.id course_id,c.name course_name,c.domain_id,c.domain_name,
                        row_number() over(partition by a.quiz_id,a.revision order by a.taken_at desc,a.id desc) latest_row,
                        row_number() over(partition by a.quiz_id,a.revision order by a.score*1.0/a.total desc,a.taken_at desc,a.id desc) best_row,
                        count(*) over(partition by a.quiz_id,a.revision) attempt_count
                        from quiz_attempts a join quizzes q on q.id=a.quiz_id join lessons l on l.id=q.lesson_id join visible c on c.id=l.course_id where a.user_id=:uid and a.total>0),
                        grouped as (select a.id latest_id,b.id best_id,a.quiz_id,a.revision,a.taken_at,a.course_name,a.quiz_title,a.lesson_title from attempts_visible a join attempts_visible b on a.quiz_id=b.quiz_id and a.revision=b.revision and b.best_row=1 where a.latest_row=1),
                        legacy as (select s.*,coalesce(l.title,c.name) title,c.name course_name from user_quiz_scores s join visible c on c.id=s.course_id left join lessons l on l.id=s.lesson_id where s.user_id=:uid and s.total>0 and not exists(select 1 from attempts_visible a where a.course_id=s.course_id and (a.lesson_id=s.lesson_id or (a.lesson_id is null and s.lesson_id is null))))
                        """,
                new HashMap<>(c.params()));
    }

    public Results results(LearnerQueryScope u, int page, int size, String search) {
        var c = attempts(u);
        var p = c.params();
        p.put("limit", Math.max(1, Math.min(100, size)));
        p.put("offset", (long) Math.max(0, Math.min(1000000, page)) * (int) p.get("limit"));
        String where = "";
        if (search != null && !search.isBlank()) {
            p.put("search", "%" + LearnerDashboardSqlRelations.normalize(search.trim()).replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%");
            where = " where "
                    + LearnerDashboardSqlRelations.normalizedSql("coalesce(quiz_title,lesson_title) || ' ' || course_name")
                    + " like :search escape '!'";
        }
        var groups = jdbc.query(c.sql()
                + "select a.*,b.id best_id,b.score best_score,b.total best_total,b.passing_score best_passing_score,b.passed best_passed,b.taken_at best_taken_at from (select * from grouped"
                + where + " order by taken_at desc,quiz_id,revision limit " + p.get("limit") + " offset "
                + p.get("offset")
                + ") g join attempts_visible a on a.id=g.latest_id join attempts_visible b on b.id=g.best_id order by a.taken_at desc,a.quiz_id,a.revision",
                p, (r, n) -> {
                    var latest = attempt(r);
                    var best = new LearnerWorkspaceDto.Attempt(r.getString("best_id"), latest.quizId(),
                            latest.courseId(), latest.lessonId(), latest.title(), latest.domainId(),
                            latest.domainName(), latest.revision(), r.getInt("best_score"), r.getInt("best_total"),
                            r.getInt("best_passing_score"), r.getBoolean("best_passed"),
                            r.getObject("best_taken_at", LocalDateTime.class));
                    return new Group(latest, best, r.getLong("attempt_count"), r.getString("course_name"));
                });
        long total = jdbc.queryForObject(c.sql() + "select count(*) from grouped" + where, p, Long.class);
        Double best = jdbc.queryForObject(c.sql()
                + "select max(score_value) from (select score*100.0/total score_value from attempts_visible union all select score*100.0/total score_value from legacy) scores",
                p, Double.class);
        var legacy = jdbc.query(c.sql() + "select * from legacy order by taken_at desc,id desc limit " + p.get("limit")
                + " offset " + p.get("offset") + "", p, (r, n) -> new LegacyScore(r.getString("course_id"),
                        r.getString("lesson_id"), r.getString("title"), r.getString("course_name"), r.getInt("score"),
                        r.getInt("total"), r.getObject("taken_at", LocalDateTime.class)));
        return new Results(groups, total, best == null ? null : (int) Math.round(best), legacy,
                jdbc.queryForObject(c.sql() + "select count(*) from legacy", p, Long.class));
    }

    public List<LearnerWorkspaceDto.Attempt> history(LearnerQueryScope u,String quiz,int revision,LocalDateTime time,String id,int limit) {
        var c=relations.visible(u);var p=c.params();p.put("quiz",quiz);p.put("revision",revision);
        String before="";
        if(time!=null){p.put("time",time);p.put("id",id);before=" and (a.taken_at<:time or (a.taken_at=:time and a.id<:id))";}
        return jdbc.query(c.sql()
                + "select a.id,a.quiz_id,a.revision,a.score,a.total,a.passing_score,a.passed,a.taken_at,q.title quiz_title,l.title lesson_title,l.id lesson_id,c.id course_id,c.name course_name,c.domain_id,c.domain_name from quiz_attempts a join quizzes q on q.id=a.quiz_id join lessons l on l.id=q.lesson_id join visible c on c.id=l.course_id where a.user_id=:uid and a.total>0 and a.quiz_id=:quiz and a.revision=:revision"
                + before
                + " order by a.taken_at desc,a.id desc limit " + limit, p, (r, n) -> attempt(r));
    }

    public List<Competency> competencies(LearnerQueryScope u) {
        var a = attempts(u);
        var f = relations.facts(u);
        String sql = f.sql() + a.sql().substring(a.sql().indexOf(", attempts_visible"))
                + """
                        , evaluated as (select a.*,row_number() over(partition by quiz_id order by taken_at desc,id desc) quiz_row from attempts_visible a where latest_row=1), domains as (select distinct domain_id,domain_name from facts where source in ('ASSIGNED','REGISTERED'))
                        select d.domain_id,d.domain_name,count(b.id) score_count,avg(b.score*100.0/b.total) score_value from domains d left join evaluated e on e.domain_id=d.domain_id and e.quiz_row=1 left join attempts_visible b on b.quiz_id=e.quiz_id and b.revision=e.revision and b.best_row=1 group by d.domain_id,d.domain_name order by d.domain_id
                        """;
        return jdbc.query(sql, f.params(), (r, n) -> {
            java.math.BigDecimal score = r.getBigDecimal(4);
            return new Competency(r.getString(1), r.getString(2), r.getLong(3),
                    score == null ? null : Math.round(score.doubleValue()));
        });
    }

    private LearnerWorkspaceDto.Attempt attempt(java.sql.ResultSet r) throws java.sql.SQLException {
        return new LearnerWorkspaceDto.Attempt(r.getString("id"), r.getString("quiz_id"), r.getString("course_id"),
                r.getString("lesson_id"),
                r.getString("quiz_title") == null ? r.getString("lesson_title") : r.getString("quiz_title"),
                r.getString("domain_id"), r.getString("domain_name"), r.getInt("revision"), r.getInt("score"),
                r.getInt("total"), r.getInt("passing_score"), r.getBoolean("passed"),
                r.getObject("taken_at", LocalDateTime.class));
    }
}
