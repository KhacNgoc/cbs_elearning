package com.bidv.coretraining.report.repository.impl;
import com.bidv.coretraining.report.dto.TrainingReportQueryDto.Filter;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/**
 * Authorized report facts. Counts, filters and page boundaries are evaluated in
 * SQL.
 */
@Repository
public class TrainingReportQueryRepositoryImpl implements TrainingReportQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public TrainingReportQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Context(String sql, Map<String, Object> params) {
    }

    private static final Map<String, String> DIMENSIONS = Map.of("learners", "learner_id", "courses", "course_name",
            "programs", "program", "units", "unit", "domains", "domain");

    private static String published(String a) {
        return "(" + a + ".status is null or upper(" + a + ".status) in ('ACTIVE','PUBLISHED'))";
    }

    public List<Unit> units() {
        return jdbc.query("select id,parent_id,active from organization_units",Map.of(),(rs,i)->new Unit(rs.getString(1),rs.getString(2),rs.getBoolean(3)));
    }
    private Context facts(Scope authorized) {
        Map<String,Object> p=new HashMap<>(); p.put("today",LocalDate.now());
        List<String> allowed=new ArrayList<>();
        int index=0;
        for(var role:authorized.roles()) {
            if(role.unitIds().isEmpty()) continue;
            String name="scope"+(index++); p.put(name,role.unitIds());
            String part="u.unit_id in (:"+name+")";
            if(role.assignedCoursesOnly()) {
                p.put("actor",authorized.actorId());
                part+=" and exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=:actor)";
            }
            allowed.add("("+part+")");
        }
        String scope=authorized.system() ? "1=1" : allowed.isEmpty() ? "1=0" : "("+String.join(" or ",allowed)+")";
        String ancestors = "with recursive active_units(id,parent_id) as (select id,parent_id from organization_units where parent_id is null and active=true union all select n.id,n.parent_id from organization_units n join active_units p on p.id=n.parent_id where n.active=true), ancestors(child_id,ancestor_id,parent_id) as (select id,id,parent_id from active_units union all select a.child_id,p.id,p.parent_id from ancestors a join active_units p on p.id=a.parent_id)";
        String mandatory = "exists(select 1 from course_unit_grants g join ancestors a on a.ancestor_id=g.unit_id where a.child_id=u.unit_id and g.course_id=c.id and upper(g.grant_type)='MANDATORY')";
        String sql = ancestors
                + ", visible_lessons as (select l.id,l.course_id,l.chapter_id from lessons l join chapters ch on ch.id=l.chapter_id where "
                + published("l") + " and " + published("ch")
                + "), assigned as (select u.id learner_id,u.full_name name,c.id course_id,c.name course_name,c.quiz_passing_score passing_score,p.name program,d.name domain,coalesce(ou.name,'Chưa có đơn vị') unit,case when "
                + mandatory
                + " then 1 else 0 end mandatory,case when exists(select 1 from user_course_deadlines dd where dd.user_id=u.id and dd.course_id=c.id) then (select dd.due_date from user_course_deadlines dd where dd.user_id=u.id and dd.course_id=c.id) else c.completion_deadline end due_date,(select cc.completed_at from user_completed_courses cc where cc.user_id=u.id and cc.course_id=c.id) completion_date from users u cross join courses c join training_programs p on p.id=c.program_id join training_domains d on d.id=p.domain_id left join organization_units ou on ou.id=u.unit_id where c.status='PUBLISHED' and "
                + scope + " and (" + mandatory
                + " or exists(select 1 from user_registered_courses rc where rc.user_id=u.id and rc.course_id=c.id))), scored as (select s.id,s.user_id,s.course_id,s.lesson_id,s.score*100.0/s.total score,s.taken_at,case when s.lesson_id is null then c.quiz_passing_score else coalesce(q.passing_score,80) end passing_score,coalesce(l.title,c.name) assessment,row_number() over(partition by s.user_id,s.course_id,s.lesson_id order by s.taken_at desc,s.id desc) latest,row_number() over(partition by s.user_id,s.course_id,s.lesson_id order by s.taken_at,s.id) first_attempt from user_quiz_scores s join courses c on c.id=s.course_id left join lessons l on l.id=s.lesson_id left join quizzes q on q.lesson_id=l.id where s.total>0 and (s.lesson_id is null or exists(select 1 from visible_lessons vl where vl.id=s.lesson_id)) and exists(select 1 from assigned a where a.learner_id=s.user_id and a.course_id=s.course_id)), assessments as (select user_id,course_id,lesson_id,count(*) attempt_count,max(case when latest=1 then score else null end) latest_score,max(case when floor(score+0.5)>=passing_score then 1 else 0 end) ever_passed,max(case when first_attempt=1 and floor(score+0.5)>=passing_score then 1 else 0 end) first_passed from scored group by user_id,course_id,lesson_id), assessment_stats as (select user_id,course_id,sum(attempt_count) attempt_count,avg(latest_score) average,min(ever_passed) ever_passed,min(first_passed) first_passed from assessments group by user_id,course_id), progress_facts as (select a.*,coalesce(ast.attempt_count,0) attempt_count,ast.average,coalesce(ast.ever_passed,0) ever_passed,coalesce(ast.first_passed,0) first_passed,(select count(*) from visible_lessons l where l.course_id=a.course_id) total_lessons,(select count(*) from visible_lessons l join user_completed_lessons cl on cl.lesson_id=l.id where l.course_id=a.course_id and cl.user_id=a.learner_id) done_lessons,case when exists(select 1 from visible_lessons l join quizzes q on q.lesson_id=l.id where l.course_id=a.course_id and q.required_for_progress=true and exists(select 1 from questions qu where qu.quiz_id=q.id) and not exists(select 1 from user_quiz_scores qs where qs.user_id=a.learner_id and qs.lesson_id=l.id and qs.total>0 and floor(qs.score*100.0/qs.total+0.5)>=q.passing_score)) then 0 else 1 end required_passed,case when exists(select 1 from chapters ch where ch.course_id=a.course_id and "
                + published("ch") + ") and not exists(select 1 from chapters ch where ch.course_id=a.course_id and "
                + published("ch")
                + " and not exists(select 1 from visible_lessons l where l.chapter_id=ch.id)) then 1 else 0 end nonempty,case when exists(select 1 from lesson_progress lp join visible_lessons l on l.id=lp.lesson_id where lp.user_id=a.learner_id and l.course_id=a.course_id) then 1 else 0 end accessed from assigned a left join assessment_stats ast on ast.user_id=a.learner_id and ast.course_id=a.course_id), facts as (select pf.*,case when nonempty=1 and total_lessons=done_lessons and required_passed=1 then 1 else 0 end completed,case when nonempty=1 and total_lessons=done_lessons and required_passed=1 then cast(completion_date as date) else null end completed_at,case when nonempty=1 and total_lessons=done_lessons and required_passed=1 then 'Hoàn thành' when done_lessons=0 and attempt_count=0 and accessed=0 then 'Chưa bắt đầu' else 'Đang học' end status from progress_facts pf) ";
        return new Context(sql, p);
    }

    private static String like(String s) {
        return "%" + (s == null ? "" : s.trim().toLowerCase(Locale.ROOT)).replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    private String filtered(Filter f, Map<String, Object> p) {
        String where = " where 1=1";
        String[][] values = { { "unit", f.unit() }, { "program", f.program() },
                { "course_name", f.course() } };
        for (int i = 0; i < values.length; i++)
            if (values[i][1] != null && !values[i][1].isBlank()) {
                where += " and " + values[i][0] + "=:filter" + i;
                p.put("filter" + i, values[i][1]);
            }
        if (f.from() != null && !f.from().isBlank()) {
            LocalDate date = LocalDate.parse(f.from());
            p.put("from", date);
            where += " and completed_at>=:from";
        }
        if (f.to() != null && !f.to().isBlank()) {
            LocalDate date = LocalDate.parse(f.to());
            p.put("to", date);
            where += " and completed_at<=:to";
        }
        if ("Quá hạn".equals(f.status()))
            where += " and completed=0 and due_date<:today";
        else if (f.status() != null && !f.status().isBlank()) {
            p.put("status", f.status());
            where += " and status=:status";
        }
        if (f.dimension() != null && DIMENSIONS.containsKey(f.dimension()) && f.key() != null && !f.key().isBlank()) {
            p.put("key", f.key());
            where += " and cast(" + DIMENSIONS.get(f.dimension()) + " as varchar(255))=:key";
        }
        return where;
    }

    private static String summaryColumns() {
        return "count(*) total,count(distinct learner_id) people,count(distinct case when attempt_count>0 then learner_id else null end) evaluated_people,coalesce(sum(completed),0) complete,coalesce(sum(case when status='Đang học' then 1 else 0 end),0) learning,coalesce(sum(case when status='Chưa bắt đầu' then 1 else 0 end),0) not_started,coalesce(sum(case when attempt_count>0 then 1 else 0 end),0) tested,coalesce(sum(case when attempt_count>0 and ever_passed=1 and required_passed=1 then 1 else 0 end),0) passed,coalesce(sum(case when attempt_count>0 and (ever_passed=0 or required_passed=0) then 1 else 0 end),0) failed,coalesce(sum(case when attempt_count>0 and first_passed=1 and required_passed=1 then 1 else 0 end),0) first_passed,avg(average) average,coalesce(sum(case when due_date is not null then 1 else 0 end),0) with_deadline,coalesce(sum(case when completed=0 and due_date<:today then 1 else 0 end),0) overdue,count(distinct case when completed=0 and due_date<:today then learner_id else null end) overdue_people,count(distinct case when due_date is not null then learner_id else null end) people_with_deadline,coalesce(sum(mandatory),0) mandatory,coalesce(sum(case when completed=1 then mandatory else 0 end),0) mandatory_complete";
    }

    private static Map<String, Object> camel(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            StringBuilder name = new StringBuilder();
            boolean upper = false;
            for (char c : key.toLowerCase(Locale.ROOT).toCharArray())
                if (c == '_')
                    upper = true;
                else {
                    name.append(upper ? Character.toUpperCase(c) : c);
                    upper = false;
                }
            result.put(name.toString(), value instanceof java.sql.Timestamp t ? t.toLocalDateTime()
                    : value instanceof java.sql.Date d ? d.toLocalDate() : value);
        });
        return result;
    }

    private List<Map<String, Object>> maps(String sql, Map<String, Object> p) {
        return jdbc.queryForList(sql, p).stream().map(TrainingReportQueryRepositoryImpl::camel).toList();
    }

    private static String action(String action) {
        return switch (action == null ? "" : action) {
            case "overdue" -> "completed=0 and due_date<:today";
            case "dueSoon" -> "status='Chưa bắt đầu' and due_date>=:today and due_date<=:soon";
            case "failed" -> "attempt_count>0 and (ever_passed=0 or required_passed=0)";
            case "untested" -> "attempt_count=0";
            case "incomplete" -> "completed=0";
            default -> "1=1";
        };
    }

    public Map<String, Object> read(Scope scope, Filter f, boolean metadata) {
        var c = facts(scope);
        var p = new HashMap<>(c.params());
        String where = filtered(f, p);
        p.put("soon", LocalDate.now().plusDays(7));
        String base = c.sql() + ", selected as (select * from facts" + where + ") ";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", LocalDate.now());
        if (metadata)
            result.put("summary", maps(base + "select " + summaryColumns() + " from selected", p).get(0));
        Map<String, Object> choices = new LinkedHashMap<>();
        if (metadata)
            for (var entry : Map
                    .of("units", "unit", "programs", "program", "courses", "course_name")
                    .entrySet())
                choices.put(entry.getKey(), jdbc.query(
                        c.sql() + " select distinct " + entry.getValue() + " from facts order by " + entry.getValue(),
                        p, (r, i) -> r.getString(1)));
        result.put("choices", choices);
        String dimension = DIMENSIONS.get(f.view());
        int size = Math.max(1, Math.min(f.size(), 100)), page = Math.max(0, Math.min(f.page(), 1000000));
        String bounds = " limit " + size + " offset " + ((long) page * size);
        String search = "";
        if (f.search() != null && !f.search().isBlank()) {
            p.put("search", like(f.search()));
            search = " and lower(coalesce(name,'')||' '||course_name||' '||unit||' '||cast(learner_id as varchar(255))) like :search escape '!'";
        }
        if (dimension == null) {
            String selection = " where " + ("overview".equals(f.view()) ? action(f.action()) : "1=1") + search;
            result.put("total", jdbc.queryForObject(base + "select count(*) from selected" + selection, p, Long.class));
            result.put("items", maps(base
                    + "select learner_id,name,unit,course_id,course_name,program,domain,mandatory,due_date,completed_at,completed,status,attempt_count,average,ever_passed,required_passed,passing_score,case when total_lessons=0 then 0 else floor(done_lessons*100.0/total_lessons+0.5) end progress from selected"
                    + selection + " order by learner_id,course_id" + bounds, p));
        } else {
            String grouped = base + ", grouped as (select cast(" + dimension
                    + " as varchar(255)) name,min(name) learner_name," + summaryColumns() + " from selected group by "
                    + dimension + ") ";
            String filter = f.search() == null || f.search().isBlank() ? ""
                    : " where lower(name||' '||learner_name) like :search escape '!'";
            result.put("total", jdbc.queryForObject(grouped + "select count(*) from grouped" + filter, p, Long.class));
            result.put("items", maps(grouped + "select * from grouped" + filter + " order by name" + bounds, p));
        }
        result.put("page", page);
        result.put("size", size);
        if (!metadata)
            return result;
        List<Map<String, Object>> actions = new ArrayList<>();
        for (String a : List.of("overdue", "dueSoon", "failed", "untested", "incomplete"))
            actions.add(Map.of("id", a, "count", Objects.requireNonNull(
                    jdbc.queryForObject(base + "select count(*) from selected where " + action(a), p, Long.class))));
        result.put("actions", actions);
        Map<String, Object> charts = new LinkedHashMap<>();
        for (String d : List.of("units", "courses", "programs", "domains")) {
            String col = DIMENSIONS.get(d);
            charts.put(d, maps(base + "select " + col + " name," + summaryColumns() + " from selected group by " + col
                    + " order by count(*) desc," + col + " limit 20", p));
        }
        result.put("charts", charts);
        result.put("months", maps(base
                + "select extract(year from completed_at) as \"year\",extract(month from completed_at) as \"month\",count(*) count from selected where completed_at is not null group by extract(year from completed_at),extract(month from completed_at) order by 1,2",
                p));
        result.put("bins", maps(base
                + "select case when average<50 then 'Dưới 50' when average<70 then '50–69' when average<85 then '70–84' else '85–100' end name,count(*) count from selected where attempt_count>0 group by case when average<50 then 'Dưới 50' when average<70 then '50–69' when average<85 then '70–84' else '85–100' end",
                p));
        return result;
    }

    /**
     * Stream the active view without collecting its full result in application
     * memory.
     */
    public void export(Scope scope, Filter f, java.util.function.Consumer<Map<String,Object>> row) {
        var c = facts(scope);
        var p = new HashMap<>(c.params());
        p.put("soon", LocalDate.now().plusDays(7));
        String base = c.sql() + ", selected as (select * from facts" + filtered(f, p) + ") ";
        String dimension = DIMENSIONS.get(f.view()), sql;
        if (dimension != null) {
            sql = base + ", grouped as (select cast(" + dimension + " as varchar(255)) name,min(name) learner_name,"
                    + summaryColumns() + " from selected group by " + dimension + ") select * from grouped";
            if (f.search() != null && !f.search().isBlank()) {
                p.put("search", like(f.search()));
                sql += " where lower(name||' '||learner_name) like :search escape '!'";
            }
            sql += " order by name";
        } else {
            sql = base + "select * from selected where " + ("overview".equals(f.view()) ? action(f.action()) : "1=1");
            if (f.search() != null && !f.search().isBlank()) {
                p.put("search", like(f.search()));
                sql += " and lower(coalesce(name,'')||' '||course_name||' '||unit||' '||cast(learner_id as varchar(255))) like :search escape '!'";
            }
            sql += " order by learner_id,course_id";
        }
        // A positive fetch size and the service's read-only transaction keep
        // PostgreSQL from buffering the export.
        jdbc.execute(sql, p, (org.springframework.jdbc.core.PreparedStatementCallback<Void>) statement -> {
            statement.setFetchSize(500);
            try (var rs = statement.executeQuery()) {
                var columns = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (int i = 1; i <= columns.getColumnCount(); i++)
                        values.put(columns.getColumnLabel(i), rs.getObject(i));
                    row.accept(camel(values));
                }
            }
            return null;
        });
    }

    public List<Map<String,Object>> attempts(Scope scope, long learner, String course, AttemptBoundary before, int limit) {
        var c=facts(scope); var p=new HashMap<>(c.params());
        p.put("learner",learner); p.put("course",course);
        String boundary="";
        if(before != null) {
            p.put("time",before.time()); p.put("id",before.id());
            boundary=" and (taken_at<:time or (taken_at=:time and id<:id))";
        }
        return maps(c.sql()+" select id,score,taken_at,passing_score,assessment from scored where user_id=:learner and course_id=:course"+boundary+" order by taken_at desc,id desc limit "+(limit+1),p);
    }

    public boolean attemptVisible(Scope scope, long attemptId) {
        var c=facts(scope); var p=new HashMap<>(c.params());
        p.put("attempt",attemptId);
        return !maps(c.sql()+" select id from scored where id=:attempt limit 1",p).isEmpty();
    }
}
