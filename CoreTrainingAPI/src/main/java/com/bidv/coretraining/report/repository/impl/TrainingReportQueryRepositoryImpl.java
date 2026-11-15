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

    private record Context(String sql, Map<String, Object> params, String scope) {
    }

    private Boolean postgres;

    /**
     * Stops PostgreSQL choosing nested loops for this transaction's report statements. The planner has no
     * statistics for grouped CTEs and estimates single-digit rows for tens of thousands, so it rescans whole CTEs
     * per row: minutes on 1.7k learners. Hash joins cost little more when the rows really are few. The larger
     * work_mem keeps those hash joins off the temp disk; it is per plan node, for this transaction only.
     * Parallel workers still run, but each builds its own hash table: a shared (parallel) hash table lives in
     * /dev/shm, and the report's many 64MB hash joins overflow it ("could not resize shared memory segment ...
     * No space left on device"; Docker gives a container 64MB). On 10k learners / 1M progress rows the
     * statement takes ~4.5s this way, against ~5.6s with no parallelism at all.
     */
    private void avoidNestedLoops() {
        if (postgres == null)
            postgres = jdbc.getJdbcTemplate().execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
                    "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
        if (postgres)
            jdbc.queryForObject("select set_config('enable_nestloop','off',true)||set_config('work_mem','64MB',true)"
                    + "||set_config('enable_parallel_hash','off',true)", Map.of(), String.class);
    }

    private static final String UNIT_NAME = "coalesce(ou.name,'Chưa có đơn vị')";
    /** The grouping column of each dimension, on named_cells, unit_named and pair_named. */
    private static final Map<String, String> DIMENSIONS = Map.of("courses", "course_name", "programs", "program", "units", "unit", "domains", "domain");
    /** The same dimensions, on course_dim cd and organization_units ou. */
    private static final Map<String, String> DIMENSION_NAMES = Map.of("courses", "cd.course_name", "programs", "cd.program", "units", UNIT_NAME, "domains", "cd.domain");

    private static String published(String a) {
        return "(" + a + ".status is null or upper(" + a + ".status) in ('ACTIVE','PUBLISHED'))";
    }

    public List<Unit> units() {
        return jdbc.query("select id,parent_id,active from organization_units",Map.of(),(rs,i)->new Unit(rs.getString(1),rs.getString(2),rs.getBoolean(3)));
    }
    private Context facts(Scope authorized) {
        return facts(authorized, "");
    }

    /**
     * Facts are stored compactly because learners x courses reaches millions of rows, of which most are simply
     * "assigned, nothing done yet". Two kinds of row represent them:
     * <ul>
     * <li>one row per unit and mandatory course, in zero-activity state, whose {@code weight} is the members of
     * the unit without personal facts for that course ({@code owner} is null);</li>
     * <li>one real row per learner and course with personal facts: registered outside the mandatory list, or with
     * activity ({@code owner} and {@code learner_id} are the learner, {@code weight} is 1).</li>
     * </ul>
     * The rows carry ids and flags only, since every copy of them is held in memory or on the temp disk; names are
     * joined after aggregating. {@code learnerCondition} limits the learners, on columns of {@code users}.
     */
    private Context facts(Scope authorized, String learnerCondition) {
        Map<String,Object> p=new HashMap<>(); p.put("today",LocalDate.now()); p.put("soon",LocalDate.now().plusDays(7));
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
        // Every role of a scoped actor limits learners to its units (scope tests u.unit_id), so the learners of
        // other units are dropped before their activity is read rather than after: a branch report then scans
        // the activity of its branch, not of the whole bank.
        if (!authorized.system()) {
            Set<String> units = new HashSet<>();
            authorized.roles().forEach(role -> units.addAll(role.unitIds()));
            p.put("learnerUnits", units);
            String inUnits = units.isEmpty() ? "1=0" : "unit_id in (:learnerUnits)";
            learnerCondition = learnerCondition.isEmpty() ? inUnits : "(" + learnerCondition + ") and " + inUnits;
        }
        String ancestors = "with recursive active_units(id,parent_id) as (select id,parent_id from organization_units where parent_id is null and active=true union all select n.id,n.parent_id from organization_units n join active_units p on p.id=n.parent_id where n.active=true), ancestors(child_id,ancestor_id,parent_id) as (select id,id,parent_id from active_units union all select a.child_id,p.id,p.parent_id from ancestors a join active_units p on p.id=a.parent_id)";
        String scoped = learnerCondition.isEmpty() ? "" : " where t.user_id in (select id from scoped_users)";
        String touched = "select t.user_id,t.course_id from user_quiz_scores t" + scoped
                + " union all select t.user_id,t.course_id from user_course_deadlines t" + scoped
                + " union all select t.user_id,t.course_id from user_completed_courses t" + scoped;
        touched += " union all select t.user_id,l.course_id from user_quiz_scores t join lessons l on l.id=t.lesson_id" + scoped;
        touched += " union all select t.user_id,t.course_id from lesson_activity t";
        touched += " union all select t.user_id,l.course_id from quiz_attempts t join quizzes q on q.id=t.quiz_id join lessons l on l.id=q.lesson_id" + scoped;
        // Lesson progress and completions are the largest tables (one row per learner and lesson). They are read
        // once, together, and kept per learner and course: who touched the course, who opened a visible lesson
        // (accessed) and how many visible lessons they completed. Reading them separately for the touched marks,
        // learner_done and learner_access tripled the work and sorted every progress row to deduplicate it.
        String visible = "(" + published("l") + " and " + published("ch") + ")";
        String lessonActivity = ", lesson_activity as (select a.user_id,l.course_id,"
                + "max(case when a.kind=1 and " + visible + " then 1 else 0 end) accessed,"
                + "sum(case when a.kind=2 and " + visible + " then 1 else 0 end) done_lessons"
                + " from (select t.user_id,t.lesson_id,1 kind from lesson_progress t" + scoped
                + " union all select t.user_id,t.lesson_id,2 from user_completed_lessons t" + scoped + ") a"
                + " join lessons l on l.id=a.lesson_id join chapters ch on ch.id=l.chapter_id group by a.user_id,l.course_id)";
        String learners = ", scoped_users as (select id,full_name,unit_id from users"
                + (learnerCondition.isEmpty() ? "" : " where " + learnerCondition) + ")"
                + lessonActivity
                + ", unit_members as (select unit_id,cast(count(*) as int) members,min(full_name) full_name from scoped_users where unit_id is not null group by unit_id)"
                + ", personal_marks as (select t.user_id,t.course_id,1 touched,0 registered from (" + touched + ") t"
                + " union all select t.user_id,t.course_id,0,1 from user_registered_courses t" + scoped + ")"
                + ", personal as (select s.id learner_id,s.unit_id,pm.course_id,case when mu.course_id is null then 0 else 1 end mandatory"
                + " from (select user_id,course_id,max(touched) touched,max(registered) registered from personal_marks group by user_id,course_id) pm"
                + " join scoped_users s on s.id=pm.user_id left join mandatory_units mu on mu.unit_id=s.unit_id and mu.course_id=pm.course_id"
                + " where (mu.course_id is null and pm.registered=1) or (mu.course_id is not null and pm.touched=1))"
                + ", personal_counts as (select unit_id,course_id,count(*) n from personal where mandatory=1 group by unit_id,course_id)"
                + ", assigned_pairs as (select cast(null as bigint) learner_id,cast(null as bigint) owner,um.unit_id,um.members-coalesce(pc.n,0) weight,mu.course_id,1 mandatory from unit_members um join mandatory_units mu on mu.unit_id=um.unit_id left join personal_counts pc on pc.unit_id=um.unit_id and pc.course_id=mu.course_id"
                + " union all select learner_id,learner_id,unit_id,1,course_id,mandatory from personal)";
        // Every fact is aggregated once and joined by key: a correlated subquery here would run per learner-course pair.
        String sql = ancestors
                + ", mandatory_units as (select distinct g.course_id,a.child_id unit_id from course_unit_grants g join ancestors a on a.ancestor_id=g.unit_id where upper(g.grant_type)='MANDATORY')"
                + learners
                + ", visible_lessons as (select l.id,l.course_id,l.chapter_id from lessons l join chapters ch on ch.id=l.chapter_id where "
                + published("l") + " and " + published("ch")
                + "), course_dim as (select c.id,c.name course_name,c.quiz_passing_score passing_score,c.completion_deadline,p.name program,d.name domain from courses c join training_programs p on p.id=c.program_id join training_domains d on d.id=p.domain_id where c.status='PUBLISHED')"
                + ", assigned as (select u.learner_id,u.owner,u.weight,u.unit_id,u.course_id,u.mandatory,case when dd.user_id is null then c.completion_deadline else dd.due_date end due_date,cc.completed_at completion_date from assigned_pairs u join course_dim c on c.id=u.course_id left join user_course_deadlines dd on dd.user_id=u.learner_id and dd.course_id=c.id left join user_completed_courses cc on cc.user_id=u.learner_id and cc.course_id=c.id where "
                + scope
                + "), scored as (select s.id,s.user_id,s.course_id,s.lesson_id,s.score*100.0/s.total score,s.taken_at,case when s.lesson_id is null then c.quiz_passing_score else coalesce(q.passing_score,80) end passing_score,coalesce(l.title,c.name) assessment,row_number() over(partition by s.user_id,s.course_id,s.lesson_id order by s.taken_at desc,s.id desc) latest,row_number() over(partition by s.user_id,s.course_id,s.lesson_id order by s.taken_at,s.id) first_attempt from user_quiz_scores s join personal ga on ga.learner_id=s.user_id and ga.course_id=s.course_id join courses c on c.id=s.course_id left join lessons l on l.id=s.lesson_id left join quizzes q on q.lesson_id=l.id left join visible_lessons vs on vs.id=s.lesson_id where s.total>0 and (s.lesson_id is null or vs.id is not null))"
                + ", assessments as (select user_id,course_id,lesson_id,count(*) attempt_count,max(case when latest=1 then score else null end) latest_score,max(case when floor(score+0.5)>=passing_score then 1 else 0 end) ever_passed,max(case when first_attempt=1 and floor(score+0.5)>=passing_score then 1 else 0 end) first_passed from scored group by user_id,course_id,lesson_id)"
                + ", assessment_stats as (select user_id,course_id,sum(attempt_count) attempt_count,avg(latest_score) average,min(ever_passed) ever_passed,min(first_passed) first_passed from assessments group by user_id,course_id)"
                + ", course_totals as (select course_id,count(*) total_lessons from visible_lessons group by course_id)"
                + ", learner_done as (select course_id,user_id,done_lessons from lesson_activity where done_lessons>0)"
                + ", required_quizzes as (select l.id lesson_id,l.course_id,q.passing_score from visible_lessons l join quizzes q on q.lesson_id=l.id where q.required_for_progress=true and exists(select 1 from questions qu where qu.quiz_id=q.id))"
                + ", required_totals as (select course_id,count(*) required_count from required_quizzes group by course_id)"
                + ", required_met as (select rq.course_id,qs.user_id,count(distinct rq.lesson_id) met from required_quizzes rq join user_quiz_scores qs on qs.lesson_id=rq.lesson_id and qs.total>0 and floor(qs.score*100.0/qs.total+0.5)>=rq.passing_score join scoped_users u on u.id=qs.user_id group by rq.course_id,qs.user_id)"
                + ", course_shape as (select c.id course_id,case when exists(select 1 from chapters ch where ch.course_id=c.id and "
                + published("ch") + ") and not exists(select 1 from chapters ch where ch.course_id=c.id and "
                + published("ch")
                + " and not exists(select 1 from visible_lessons l where l.chapter_id=ch.id)) then 1 else 0 end nonempty from courses c where c.status='PUBLISHED')"
                + ", learner_access as (select course_id,user_id from lesson_activity where accessed=1)"
                + ", progress_facts as (select a.*,coalesce(ast.attempt_count,0) attempt_count,ast.average,coalesce(ast.ever_passed,0) ever_passed,coalesce(ast.first_passed,0) first_passed,coalesce(ct.total_lessons,0) total_lessons,coalesce(ld.done_lessons,0) done_lessons,case when coalesce(rt.required_count,0)=coalesce(rm.met,0) then 1 else 0 end required_passed,coalesce(cs.nonempty,0) nonempty,case when la.user_id is null then 0 else 1 end accessed from assigned a left join assessment_stats ast on ast.user_id=a.learner_id and ast.course_id=a.course_id left join course_totals ct on ct.course_id=a.course_id left join learner_done ld on ld.course_id=a.course_id and ld.user_id=a.learner_id left join required_totals rt on rt.course_id=a.course_id left join required_met rm on rm.course_id=a.course_id and rm.user_id=a.learner_id left join course_shape cs on cs.course_id=a.course_id left join learner_access la on la.course_id=a.course_id and la.user_id=a.learner_id)"
                + ", facts as (select pf.*,case when attempt_count>0 then 1 else 0 end tested,case when due_date is null then 0 when due_date<:today then 1 when due_date<=:soon then 2 else 3 end due_state,case when nonempty=1 and total_lessons=done_lessons and required_passed=1 then 1 else 0 end completed,case when nonempty=1 and total_lessons=done_lessons and required_passed=1 then cast(completion_date as date) else null end completed_at,case when nonempty=1 and total_lessons=done_lessons and required_passed=1 then 'Hoàn thành' when done_lessons=0 and attempt_count=0 and accessed=0 then 'Chưa bắt đầu' else 'Đang học' end status from progress_facts pf) ";
        return new Context(sql, p, scope);
    }

    private static String escaped(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String normalized(String s) {
        return (s == null ? "" : s.trim().toLowerCase(Locale.ROOT));
    }

    private static String like(String s) {
        return "%" + escaped(normalized(s)) + "%";
    }

    /** The learner condition of a drill-down into one learner; the key parameter is set by {@link #withKey}. */
    private static String singleLearner(Filter f) {
        return "learners".equals(f.dimension()) && f.key() != null && !f.key().isBlank()
                ? "cast(id as varchar(255))=:learnerKey" : "";
    }

    private static Map<String, Object> withKey(Context c, Filter f) {
        Map<String, Object> p = new HashMap<>(c.params());
        if (!singleLearner(f).isEmpty())
            p.put("learnerKey", f.key());
        return p;
    }

    /** The facts within the filter, as a CTE named selected. It keeps the fact columns only. */
    private String selected(Filter f, Map<String, Object> p) {
        String where = " where 1=1";
        boolean names = false;
        String[][] values = { { UNIT_NAME, f.unit() }, { "cd.program", f.program() }, { "cd.course_name", f.course() } };
        for (int i = 0; i < values.length; i++)
            if (values[i][1] != null && !values[i][1].isBlank()) {
                where += " and " + values[i][0] + "=:filter" + i;
                p.put("filter" + i, values[i][1]);
                names = true;
            }
        if (f.from() != null && !f.from().isBlank()) {
            LocalDate date = LocalDate.parse(f.from());
            p.put("from", date);
            where += " and f.completed_at>=:from";
        }
        if (f.to() != null && !f.to().isBlank()) {
            LocalDate date = LocalDate.parse(f.to());
            p.put("to", date);
            where += " and f.completed_at<=:to";
        }
        if ("Quá hạn".equals(f.status()))
            where += " and f.completed=0 and f.due_state=1";
        else if (f.status() != null && !f.status().isBlank()) {
            p.put("status", f.status());
            where += " and f.status=:status";
        }
        if (singleLearner(f).isEmpty() && f.dimension() != null && DIMENSION_NAMES.containsKey(f.dimension()) && f.key() != null && !f.key().isBlank()) {
            p.put("key", f.key());
            where += " and cast(" + DIMENSION_NAMES.get(f.dimension()) + " as varchar(255))=:key";
            names = true;
        }
        return ", selected as (select f.* from facts f"
                + (names ? " join course_dim cd on cd.id=f.course_id left join organization_units ou on ou.id=f.unit_id" : "")
                + where + ") ";
    }

    /** Measures counted per learner-course row, on columns of selected and cells. */
    private static final Map<String, String> ROW_MEASURES = orderedMeasures("total", "1", "complete", "completed",
            "learning", "case when status='Đang học' then 1 else 0 end",
            "not_started", "case when status='Chưa bắt đầu' then 1 else 0 end",
            "tested", "tested",
            "passed", "case when tested=1 and ever_passed=1 and required_passed=1 then 1 else 0 end",
            "failed", "case when tested=1 and (ever_passed=0 or required_passed=0) then 1 else 0 end",
            "first_passed", "case when tested=1 and first_passed=1 and required_passed=1 then 1 else 0 end",
            "with_deadline", "case when due_state>0 then 1 else 0 end",
            "overdue", "case when completed=0 and due_state=1 then 1 else 0 end",
            "mandatory", "mandatory", "mandatory_complete", "case when completed=1 then mandatory else 0 end");
    /** Measures counted once per learner: a learner counts if any of their rows matches. */
    private static final Map<String, String> LEARNER_MEASURES = orderedMeasures("people", "1",
            "evaluated_people", "tested",
            "overdue_people", "case when completed=0 and due_state=1 then 1 else 0 end",
            "people_with_deadline", "case when due_state>0 then 1 else 0 end");
    private static final List<String> ACTION_IDS = List.of("overdue", "dueSoon", "failed", "untested", "incomplete");

    private static Map<String, String> orderedMeasures(String... pairs) {
        Map<String, String> measures = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            measures.put(pairs[i], pairs[i + 1]);
        return measures;
    }

    private static String action(String action) {
        return switch (action == null ? "" : action) {
            case "overdue" -> "completed=0 and due_state=1";
            case "dueSoon" -> "status='Chưa bắt đầu' and due_state=2";
            case "failed" -> "tested=1 and (ever_passed=0 or required_passed=0)";
            case "untested" -> "tested=0";
            case "incomplete" -> "completed=0";
            default -> "1=1";
        };
    }

    private static String columns(Collection<String> names, String format) {
        StringBuilder result = new StringBuilder();
        for (String name : names)
            result.append(result.isEmpty() ? "" : ",").append(format.replace("{}", name));
        return result.toString();
    }

    private static String learnerMeasureColumns() {
        StringBuilder result = new StringBuilder();
        for (var m : LEARNER_MEASURES.entrySet())
            result.append(result.isEmpty() ? "" : ",").append(m.getValue()).append(" ").append(m.getKey());
        return result.toString();
    }

    /**
     * What every report total is derived from, smallest first, so no measure has to scan the rows again:
     * <ul>
     * <li>{@code named_cells}: the selected rows counted per unit, course and combination of flags. Row measures
     * only need these.</li>
     * <li>{@code unit_named}, {@code pair_named} and {@code owner_named}: for the learner measures, the rows
     * matching each one per unit and course, how a learner's personal facts change those counts per course
     * (zero changes are left out), and the same per learner and program.</li>
     * </ul>
     */
    private static String rollups() {
        String bin = "case when tested=0 then null when average<50 then 'Dưới 50' when average<70 then '50–69' when average<85 then '70–84' else '85–100' end";
        String keys = "unit_id,course_id,mandatory,status,completed,tested,ever_passed,required_passed,first_passed,due_state,y,m,bin";
        List<String> measures = new ArrayList<>(LEARNER_MEASURES.keySet());
        String deltas = columns(measures, "coalesce(r.{},0)-coalesce(u.{},0) {}");
        String changed = String.join(" or ", measures.stream().map(m -> "coalesce(r." + m + ",0)<>coalesce(u." + m + ",0)").toList());
        return ", cells as (select " + keys + ",sum(weight) n,sum(average) average_sum,count(average) average_count from (select s.*,extract(year from completed_at) y,extract(month from completed_at) m," + bin + " bin from selected s) r group by " + keys + ")"
                + ", named_cells as (select c.*,cd.course_name,cd.program,cd.domain," + UNIT_NAME + " unit from cells c join course_dim cd on cd.id=c.course_id left join organization_units ou on ou.id=c.unit_id)"
                + ", unit_bits as (select unit_id,course_id," + learnerMeasureColumns() + " from selected where owner is null)"
                + ", real_bits as (select owner,course_id," + learnerMeasureColumns() + " from selected where owner is not null)"
                + ", unit_named as (select b.*,cd.course_name,cd.program,cd.domain," + UNIT_NAME + " unit from unit_bits b join course_dim cd on cd.id=b.course_id left join organization_units ou on ou.id=b.unit_id)"
                + ", pair_named as (select x.*,cd.course_name,cd.program,cd.domain," + UNIT_NAME + " unit from (select p.learner_id owner,p.unit_id,p.course_id," + deltas + " from personal p left join real_bits r on r.owner=p.learner_id and r.course_id=p.course_id left join unit_bits u on u.unit_id=p.unit_id and u.course_id=p.course_id where " + changed + ") x join course_dim cd on cd.id=x.course_id left join organization_units ou on ou.id=x.unit_id)"
                + ", owner_named as (select owner,unit_id,unit,program,domain," + columns(measures, "sum({}) {}") + " from pair_named group by owner,unit_id,unit,program,domain)";
    }

    private static String grouping(String prefix, String column) {
        return column == null ? "cast('' as varchar(255))" : prefix + column;
    }

    /**
     * The learner measures per group, as a CTE named {@code prefix_people}. A learner counts if the unit's count
     * plus their own change is above zero; members without a change simply follow the unit.
     */
    private static String people(String prefix, String column) {
        List<String> measures = new ArrayList<>(LEARNER_MEASURES.keySet());
        String source = "courses".equals(prefix) || "course_name".equals(column) ? "pair_named" : "owner_named";
        String fromUnit = columns(measures, "um.members*case when u.{}>0 then 1 else 0 end {}");
        String fromOwner = columns(measures, "case when coalesce(u.{},0)+o.{}>0 then 1 else 0 end-case when coalesce(u.{},0)>0 then 1 else 0 end {}");
        return ", " + prefix + "_pu as (select unit_id,grp," + columns(measures, "sum({}) {}") + " from (select b.*,cast(" + grouping("b.", column) + " as varchar(255)) grp from unit_named b) r group by unit_id,grp)"
                + ", " + prefix + "_po as (select owner,unit_id,grp," + columns(measures, "sum({}) {}") + " from (select o.*,cast(" + grouping("o.", column) + " as varchar(255)) grp from " + source + " o) r group by owner,unit_id,grp)"
                + ", " + prefix + "_people as (select grp," + columns(measures, "sum({}) {}") + " from (select u.grp," + fromUnit + " from " + prefix + "_pu u join unit_members um on um.unit_id=u.unit_id"
                + " union all select o.grp," + fromOwner + " from " + prefix + "_po o left join " + prefix + "_pu u on u.unit_id=o.unit_id and u.grp=o.grp) r group by grp)";
    }

    /** The row measures of named_cells, each multiplied by the learners its cell stands for. */
    private static String cellTotals(boolean actions) {
        String result = "";
        for (var m : ROW_MEASURES.entrySet())
            result += "cast(coalesce(sum(n*(" + m.getValue() + ")),0) as bigint) " + m.getKey() + ",";
        if (actions)
            for (String a : ACTION_IDS)
                result += "cast(coalesce(sum(case when " + action(a) + " then n else 0 end),0) as bigint) \"action_" + a + "\",";
        return result + "sum(average_sum)/nullif(sum(average_count),0) average";
    }

    /** The metrics of one dimension, as CTEs ending in {@code prefix_metrics}. Null means everything is one group. */
    private static String dimensionMetrics(String prefix, String column, boolean actions) {
        String cells = column == null
                ? "select cast('' as varchar(255)) grp," + cellTotals(actions) + " from named_cells"
                : "select grp," + cellTotals(actions) + " from (select c.*,cast(c." + column + " as varchar(255)) grp from named_cells c) r group by grp having sum(n)>0";
        String metrics = columns(ROW_MEASURES.keySet(), "c.{}") + "," + columns(LEARNER_MEASURES.keySet(), "cast(coalesce(p.{},0) as bigint) {}");
        if (actions)
            metrics += "," + columns(ACTION_IDS, "c.\"action_{}\"");
        return people(prefix, column) + ", " + prefix + "_cells as (" + cells + ")"
                + ", " + prefix + "_metrics as (select c.grp," + metrics + ",c.average from " + prefix + "_cells c left join " + prefix + "_people p on p.grp=c.grp)";
    }

    /** Metrics of the view's groups, as a CTE named grouped. */
    private static String grouped(String view) {
        if ("learners".equals(view))
            return groupedLearners();
        String dimension = DIMENSION_NAMES.get(view);
        String names = "select cast(" + dimension + " as varchar(255)) grp,um.full_name learner_name from selected s join unit_members um on um.unit_id=s.unit_id join course_dim cd on cd.id=s.course_id left join organization_units ou on ou.id=s.unit_id where s.owner is null and s.weight>0"
                + " union all select cast(" + dimension + " as varchar(255)),m.full_name from selected s join scoped_users m on m.id=s.owner join course_dim cd on cd.id=s.course_id left join organization_units ou on ou.id=s.unit_id";
        return rollups() + dimensionMetrics("g", DIMENSIONS.get(view), false)
                + ", g_names as (select grp,min(learner_name) learner_name from (" + names + ") r group by grp)"
                + ", grouped as (select m.grp name,n.learner_name," + columns(ROW_MEASURES.keySet(), "m.{}") + "," + columns(LEARNER_MEASURES.keySet(), "m.{}") + ",m.average from g_metrics m left join g_names n on n.grp=m.grp) ";
    }

    private static String groupedLearners() {
        String rowColumns = "";
        for (var m : ROW_MEASURES.entrySet())
            rowColumns += "," + m.getValue() + " " + m.getKey();
        String sums = columns(ROW_MEASURES.keySet(), "sum({}) {}");
        String metrics = columns(ROW_MEASURES.keySet(), "coalesce(u.{},0)+coalesce(r.{},0)-coalesce(x.{},0) {}") + ","
                + columns(LEARNER_MEASURES.keySet(), "case when coalesce(ub.{},0)+coalesce(ob.{},0)>0 then 1 else 0 end {}");
        return rollups()
                + ", unit_rows as (select unit_id,course_id" + rowColumns + " from selected where owner is null)"
                + ", unit_sums as (select unit_id," + sums + " from unit_rows group by unit_id)"
                + ", undone_sums as (select p.learner_id owner," + columns(ROW_MEASURES.keySet(), "sum(u.{}) {}") + " from personal p join unit_rows u on u.unit_id=p.unit_id and u.course_id=p.course_id group by p.learner_id)"
                + ", real_sums as (select owner,sum(average) average_sum,count(average) average_count," + sums + " from (select owner,average" + rowColumns + " from selected where owner is not null) r group by owner)"
                + ", unit_people as (select unit_id," + columns(LEARNER_MEASURES.keySet(), "sum({}) {}") + " from unit_bits group by unit_id)"
                + ", owner_people as (select owner," + columns(LEARNER_MEASURES.keySet(), "sum({}) {}") + " from owner_named group by owner)"
                + ", grouped as (select * from (select cast(m.id as varchar(255)) name,m.full_name learner_name," + metrics + ",r.average_sum/nullif(r.average_count,0) average from scoped_users m"
                + " left join unit_sums u on u.unit_id=m.unit_id left join real_sums r on r.owner=m.id left join undone_sums x on x.owner=m.id"
                + " left join unit_people ub on ub.unit_id=m.unit_id left join owner_people ob on ob.owner=m.id) r where total>0) ";
    }

    private static final String ITEM_COLUMNS = "m.id learner_id,m.full_name name," + UNIT_NAME + " unit,s.course_id,cd.course_name,cd.program,cd.domain,s.mandatory,s.due_date,s.completed_at,s.completed,s.status,s.attempt_count,s.average,s.ever_passed,s.required_passed,cd.passing_score,case when s.total_lessons=0 then 0 else floor(s.done_lessons*100.0/s.total_lessons+0.5) end progress";
    private static final String ITEM_NAMES = " join course_dim cd on cd.id=s.course_id left join organization_units ou on ou.id=s.unit_id";
    private static final String ITEM_SEARCH = "lower(coalesce(name,'')||' '||course_name||' '||unit||' '||cast(learner_id as varchar(255))) like :search escape '!'";

    /** The CTE holding the rows of a flat view: an action narrows them, otherwise they are all of selected. */
    private static String flatRows(Filter f) {
        return "overview".equals(f.view()) && !"1=1".equals(action(f.action())) ? "selection" : "selected";
    }

    private static String selection(Filter f) {
        return "selection".equals(flatRows(f)) ? ", selection as (select * from selected where " + action(f.action()) + ") " : " ";
    }

    /**
     * The expanded rows of the learners in {@code members}: their unit's rows except for the courses they have
     * personal facts for, plus their real rows. {@code condition} applies to the result.
     */
    private static String expanded(String rows, String members, String condition) {
        return "select * from (select " + ITEM_COLUMNS + " from " + members + " m join " + rows + " s on s.owner is null and s.unit_id=m.unit_id" + ITEM_NAMES
                + " where not exists(select 1 from personal x where x.learner_id=m.id and x.mandatory=1 and x.course_id=s.course_id)"
                + " union all select " + ITEM_COLUMNS + " from " + members + " m join " + rows + " s on s.owner=m.id" + ITEM_NAMES + ") item "
                + (condition.isEmpty() ? "" : "where " + condition + " ");
    }

    /** Rows of {@code source} per learner, as a CTE named {@code prefix_n}, without expanding them. */
    private static String learnerRowCounts(String prefix, String source) {
        return ", " + prefix + "_unit as (select unit_id,count(*) n from " + source + " where owner is null group by unit_id)"
                + ", " + prefix + "_real as (select owner,count(*) n from " + source + " where owner is not null group by owner)"
                + ", " + prefix + "_undone as (select p.learner_id owner,count(*) n from personal p join " + source + " u on u.owner is null and u.unit_id=p.unit_id and u.course_id=p.course_id group by p.learner_id)"
                + ", " + prefix + "_n as (select m.id,coalesce(u.n,0)+coalesce(r.n,0)-coalesce(x.n,0) n from scoped_users m left join " + prefix + "_unit u on u.unit_id=m.unit_id left join " + prefix + "_real r on r.owner=m.id left join " + prefix + "_undone x on x.owner=m.id)";
    }

    private static final int MAX_SEARCH_SPLITS = 8;

    /**
     * The learners whose rows can only be matched by comparing whole rows: the search text starts in their name and
     * runs on past it, or ends in their id. Text can only cross between two fields at a space in the search text.
     */
    private static String acrossFields(String term, Map<String, Object> p) {
        List<String> conditions = new ArrayList<>();
        for (int at = term.indexOf(' '), i = 0; at >= 0; at = term.indexOf(' ', at + 1), i++) {
            if (i == MAX_SEARCH_SPLITS)
                return "1=1";
            p.put("searchEnd" + i, "%" + escaped(term.substring(0, at)));
            p.put("searchStart" + i, escaped(term.substring(at + 1)) + "%");
            conditions.add("lower(coalesce(m.full_name,'')) like :searchEnd" + i + " escape '!'");
            conditions.add("cast(m.id as varchar(255)) like :searchStart" + i + " escape '!'");
        }
        return conditions.isEmpty() ? "1=0" : String.join(" or ", conditions);
    }

    /**
     * The rows per learner of a flat view, as a CTE named learner_rows. With a search, nothing is expanded except
     * for {@link #acrossFields} learners: text found in the learner's name or id matches all their rows, and text
     * found in a course and unit matches that row for every learner.
     */
    private static String learnerRows(String rows, boolean searching, String term, Map<String, Object> p) {
        if (!searching)
            return learnerRowCounts("all", rows) + ", learner_rows as (select id,n row_count from all_n)";
        String whole = "lower(coalesce(m.full_name,'')) like :search escape '!' or cast(m.id as varchar(255)) like :search escape '!'";
        return learnerRowCounts("all", rows)
                + ", tail_rows as (select s.owner,s.unit_id,s.course_id from " + rows + " s" + ITEM_NAMES + " where lower(cd.course_name||' '||" + UNIT_NAME + ") like :search escape '!')"
                + learnerRowCounts("tail", "tail_rows")
                + ", across_members as (select m.id,m.full_name,m.unit_id from scoped_users m where not (" + whole + ") and (" + acrossFields(term, p) + "))"
                + ", across_n as (select learner_id id,count(*) n from (" + expanded(rows, "across_members", ITEM_SEARCH) + ") r group by learner_id)"
                + ", learner_rows as (select m.id,case when " + whole + " then a.n when c.id is not null then c.n when ac.id is not null then 0 else t.n end row_count from scoped_users m join all_n a on a.id=m.id join tail_n t on t.id=m.id left join across_n c on c.id=m.id left join across_members ac on ac.id=m.id)";
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

    private static final List<String> CHART_DIMENSIONS = List.of("units", "courses", "programs", "domains");
    /**
     * Columns of a chart point, after its name: every group of a chart dimension is sent in this compact form so charts
     * and rankings can sort and pick groups in the browser. The frontend reads them in this order (CHART_POINT_FIELDS).
     */
    private static final List<String> CHART_POINT_COLUMNS = List.of("total", "complete", "learning", "not_started", "tested",
            "passed", "failed", "with_deadline", "overdue", "mandatory", "mandatory_complete");
    private static final List<String> CHOICES =List.of("courses", "programs", "units");

    /** A row of the read statement's result: every section shares these columns and leaves the others null. */
    private static String section(String name, String group, String learnerName, String ord, String y, String m, String n, String metrics) {
        return "select '" + name + "' section,cast(" + group + " as varchar(255)) name,cast(" + learnerName + " as varchar(255)) learner_name,cast(" + ord + " as bigint) ord,cast(" + y + " as numeric) y,cast(" + m + " as numeric) m,cast(" + n + " as bigint) n," + metrics;
    }

    private static String metricColumns(String prefix) {
        return columns(ROW_MEASURES.keySet(), "cast(" + prefix + "{} as bigint) {}") + "," + columns(LEARNER_MEASURES.keySet(), "cast(" + prefix + "{} as bigint) {}") + ","
                + columns(ACTION_IDS, "cast(" + (prefix.isEmpty() ? "\"action_{}\"" : prefix + "\"action_{}\"") + " as bigint) \"action_{}\"") + ",cast(" + prefix + "average as numeric) average";
    }

    private static String noMetrics() {
        return columns(ROW_MEASURES.keySet(), "cast(null as bigint) {}") + "," + columns(LEARNER_MEASURES.keySet(), "cast(null as bigint) {}") + ","
                + columns(ACTION_IDS, "cast(null as bigint) \"action_{}\"") + ",cast(null as numeric) average";
    }

    private static String itemMetrics() {
        return columns(ROW_MEASURES.keySet(), "cast(g.{} as bigint) {}") + "," + columns(LEARNER_MEASURES.keySet(), "cast(g.{} as bigint) {}") + ","
                + columns(ACTION_IDS, "cast(null as bigint) \"action_{}\"") + ",cast(g.average as numeric) average";
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> read(Scope scope, Filter f, boolean metadata) {
        avoidNestedLoops();
        var c = facts(scope, singleLearner(f));
        var p = withKey(c, f);
        String base = c.sql() + selected(f, p);
        int size = Math.max(1, Math.min(f.size(), 100)), page = Math.max(0, Math.min(f.page(), 1000000));
        long first = (long) page * size;
        boolean searching = f.search() != null && !f.search().isBlank();
        if (searching)
            p.put("search", like(f.search()));
        boolean flat = !DIMENSIONS.containsKey(f.view()) && !"learners".equals(f.view());

        // All totals come from one statement, because each statement recomputes the facts.
        String ctes = flat ? selection(f) + learnerRows(flatRows(f), searching, normalized(f.search()), p) : grouped(f.view());
        List<String> sections = new ArrayList<>();
        if (flat) {
            p.put("first", first);
            p.put("end", first + size);
            ctes += ", positioned as (select l.*,sum(row_count) over(order by id) row_end from learner_rows l where row_count>0)";
            sections.add(section("total", "null", "null", "null", "null", "null", "coalesce(sum(row_count),0)", noMetrics()) + " from learner_rows");
            sections.add(section("page", "id", "null", "null", "null", "null", "row_end-row_count", noMetrics()) + " from positioned where row_end>:first and row_end-row_count<:end");
        } else {
            String filter = searching ? " where lower(name||' '||learner_name) like :search escape '!'" : "";
            ctes += ", shown as (select * from grouped" + filter + ")";
            sections.add(section("total", "null", "null", "null", "null", "null", "count(*)", noMetrics()) + " from shown");
            sections.add("select * from (" + section("items", "g.name", "g.learner_name", "row_number() over(order by g.name)", "null", "null", "null", itemMetrics()) + " from shown g order by g.name limit " + size + " offset " + first + ") i");
        }
        if (metadata) {
            if (flat)
                ctes += rollups();
            ctes += dimensionMetrics("summary", null, true);
            sections.add(section("summary", "null", "null", "null", "null", "null", "null", metricColumns("s.")) + " from summary_metrics s");
            for (String d : CHART_DIMENSIONS) {
                ctes += dimensionMetrics(d, DIMENSIONS.get(d), true);
                sections.add(section(d, "s.grp", "null", "null", "null", "null", "null", metricColumns("s.")) + " from " + d + "_metrics s");
            }
            sections.add(section("months", "null", "null", "null", "y", "m", "sum(n)", noMetrics()) + " from cells where y is not null group by y,m");
            sections.add(section("bins", "bin", "null", "null", "null", "null", "sum(n)", noMetrics()) + " from cells where bin is not null group by bin");
            ctes += ", chosen as (select distinct u.unit_id,u.course_id from (select unit_id,course_id from personal union select um.unit_id,mu.course_id from unit_members um join mandatory_units mu on mu.unit_id=um.unit_id) u join course_dim c on c.id=u.course_id where " + c.scope() + ")";
            String chosen = " from chosen x join course_dim cd on cd.id=x.course_id left join organization_units ou on ou.id=x.unit_id group by ";
            for (var choice : Map.of("choice_units", UNIT_NAME, "choice_programs", "cd.program", "choice_courses", "cd.course_name").entrySet())
                sections.add(section(choice.getKey(), choice.getValue(), "null", "null", "null", "null", "null", noMetrics()) + chosen + choice.getValue());
        }
        String sql = base + ctes + " select * from (" + String.join(" union all ", sections) + ") r order by section,ord,y,m,total desc,name";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", LocalDate.now());
        Map<String, Object> choices = new LinkedHashMap<>();
        Map<String, Object> charts = new LinkedHashMap<>();
        List<Map<String, Object>> actions = new ArrayList<>(), months = new ArrayList<>(), bins = new ArrayList<>(), items = new ArrayList<>();
        if (metadata)
            for (String key : CHOICES)
                choices.put(key, new ArrayList<String>());
        for (String d : CHART_DIMENSIONS)
            charts.put(d, new ArrayList<Map<String, Object>>());
        Map<String, List<List<Object>>> chartPoints = new LinkedHashMap<>();
        for (String d : CHART_DIMENSIONS)
            chartPoints.put(d, new ArrayList<>());
        List<Long> pageLearners = new ArrayList<>();
        long pageStart = Long.MAX_VALUE;
        long total = 0;
        for (var row : jdbc.queryForList(sql, p)) {
            String sectionName = (String) row.get("section");
            switch (sectionName) {
                case "total" -> total = ((Number) row.get("n")).longValue();
                case "page" -> {
                    pageLearners.add(Long.valueOf((String) row.get("name")));
                    pageStart = Math.min(pageStart, ((Number) row.get("n")).longValue());
                }
                case "items" -> {
                    Map<String, Object> item = metrics(row);
                    item.put("name", row.get("name"));
                    item.put("learnerName", row.get("learner_name"));
                    items.add(item);
                }
                case "summary" -> {
                    result.put("summary", metrics(row));
                    for (String a : ACTION_IDS)
                        actions.add(Map.of("id", a, "count", ((Number) row.get("action_" + a)).longValue()));
                }
                case "months" -> {
                    Map<String, Object> month = new LinkedHashMap<>();
                    month.put("year", row.get("y"));
                    month.put("month", row.get("m"));
                    month.put("count", ((Number) row.get("n")).longValue());
                    months.add(month);
                }
                case "bins" -> {
                    Map<String, Object> bin = new LinkedHashMap<>();
                    bin.put("name", row.get("name"));
                    bin.put("count", ((Number) row.get("n")).longValue());
                    bins.add(bin);
                }
                default -> {
                    if (sectionName.startsWith("choice_")) {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) choices.get(sectionName.substring("choice_".length()));
                        list.add((String) row.get("name"));
                    } else {
                        @SuppressWarnings("unchecked")
                        var list = (List<Map<String, Object>>) charts.get(sectionName);
                        // The statement already computes every group; charts used to see only the first 20.
                        // All of them go out compact, so sorting or picking groups needs no other report read.
                        var points = chartPoints.get(sectionName);
                        if (points != null)
                            points.add(chartPoint(row));
                        if (list.size() < 20) {
                            Map<String, Object> chart = metrics(row);
                            chart.put("name", row.get("name"));
                            list.add(chart);
                        }
                    }
                }
            }
        }
        // The choices list every learner's values, so a drill-down into one learner reads them separately.
        if (metadata && !singleLearner(f).isEmpty())
            readChoices(scope, choices);
        if (flat && !pageLearners.isEmpty())
            items.addAll(pageItems(scope, f, pageLearners, first - pageStart, size, searching));
        result.put("choices", choices);
        result.put("total", total);
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        if (!metadata)
            return result;
        result.put("actions", actions);
        result.put("charts", charts);
        result.put("chartPoints", chartPoints);
        result.put("months", months);
        result.put("bins", bins);
        return result;
    }

    /** [name, CHART_POINT_COLUMNS..., average]; the average stays null when nobody was evaluated. */
    private static List<Object> chartPoint(Map<String, Object> row) {
        List<Object> point = new ArrayList<>(CHART_POINT_COLUMNS.size() + 2);
        point.add(Objects.toString(row.get("name"), ""));
        for (String column : CHART_POINT_COLUMNS)
            point.add(row.get(column) instanceof Number n ? n.longValue() : 0L);
        point.add(row.get("average") instanceof Number n ? n.doubleValue() : null);
        return point;
    }

    private static Map<String, Object> metrics(Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String m : ROW_MEASURES.keySet())
            values.put(m, row.get(m));
        for (String m : LEARNER_MEASURES.keySet())
            values.put(m, row.get(m));
        values.put("average", row.get("average"));
        return camel(values);
    }

    /** One page of a flat view: only its learners' facts are computed, so this costs the same at any data size. */
    private List<Map<String, Object>> pageItems(Scope scope, Filter f, List<Long> learners, long skip, int size, boolean searching) {
        var c = facts(scope, "id in (:pageLearners)");
        var p = withKey(c, f);
        p.put("pageLearners", learners);
        if (searching)
            p.put("search", like(f.search()));
        return maps(c.sql() + selected(f, p) + selection(f) + expanded(flatRows(f), "scoped_users", searching ? ITEM_SEARCH : "")
                + "order by learner_id,course_id limit " + size + " offset " + skip, p);
    }

    private void readChoices(Scope scope, Map<String, Object> choices) {
        var c = facts(scope);
        choices.values().forEach(list -> ((List<?>) list).clear());
        String from = " from (select distinct unit_id,course_id from facts) x join course_dim cd on cd.id=x.course_id left join organization_units ou on ou.id=x.unit_id group by ";
        jdbc.query(c.sql() + " select * from (select 'units' section," + UNIT_NAME + " name" + from + UNIT_NAME
                + " union all select 'programs',cd.program" + from + "cd.program"
                + " union all select 'courses',cd.course_name" + from + "cd.course_name) d order by section,name", c.params(), rs -> {
                    @SuppressWarnings("unchecked")
                    List<String> list = (List<String>) choices.get(rs.getString("section"));
                    list.add(rs.getString("name"));
                });
    }

    private static final int EXPORT_LEARNERS_PER_STATEMENT = 1000;

    /**
     * Stream the active view without collecting its full result in application
     * memory. A flat view is read a range of learners at a time, so no statement
     * has to sort every learner-course row; the service's repeatable-read
     * transaction keeps the ranges on one snapshot.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void export(Scope scope, Filter f, java.util.function.Consumer<Map<String,Object>> row) {
        avoidNestedLoops();
        boolean searching = f.search() != null && !f.search().isBlank();
        if (DIMENSIONS.containsKey(f.view()) || "learners".equals(f.view())) {
            var c = facts(scope, singleLearner(f));
            var p = withKey(c, f);
            String sql = c.sql() + selected(f, p) + grouped(f.view()) + "select * from grouped";
            if (searching) {
                p.put("search", like(f.search()));
                sql += " where lower(name||' '||learner_name) like :search escape '!'";
            }
            stream(sql + " order by name", p, row);
            return;
        }
        String single = singleLearner(f);
        Long after = Long.MIN_VALUE;
        while (after != null) {
            Long upper = single.isEmpty() ? jdbc.queryForObject("select max(id) from (select id from users where id>:after order by id limit "
                    + EXPORT_LEARNERS_PER_STATEMENT + ") batch", Map.of("after", after), Long.class) : null;
            if (upper == null && single.isEmpty())
                return;
            var c = facts(scope, single.isEmpty() ? "id>:after and id<=:upper" : single);
            var p = withKey(c, f);
            p.put("after", after);
            p.put("upper", upper);
            String sql = c.sql() + selected(f, p) + selection(f);
            if (searching)
                p.put("search", like(f.search()));
            stream(sql + expanded(flatRows(f), "scoped_users", searching ? ITEM_SEARCH : "") + "order by learner_id,course_id", p, row);
            after = upper;
        }
    }

    private void stream(String sql, Map<String, Object> p, java.util.function.Consumer<Map<String,Object>> row) {
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

    /** Every recorded attempt, plus scores from before attempts were recorded, within the actor's report scope. */
    private static final String ATTEMPT_ROWS = " select * from (select cast(a.id as varchar) id,a.user_id,l.course_id,a.score*100.0/a.total score,a.taken_at,a.passing_score,l.title assessment"
            + " from quiz_attempts a join quizzes q on q.id=a.quiz_id join lessons l on l.id=q.lesson_id"
            + " where a.total>0 and exists(select 1 from visible_lessons vl where vl.id=l.id)"
            + " and exists(select 1 from assigned x where x.learner_id=a.user_id and x.course_id=l.course_id)"
            + " union all select cast(s.id as varchar) id,s.user_id,s.course_id,s.score,s.taken_at,s.passing_score,s.assessment from scored s"
            + " where exists(select 1 from assigned x where x.learner_id=s.user_id and x.course_id=s.course_id)"
            + " and not exists(select 1 from quiz_attempts a2 join quizzes q2 on q2.id=a2.quiz_id where a2.user_id=s.user_id and q2.lesson_id=s.lesson_id)) attempt_rows ";

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String,Object>> attempts(Scope scope, long learner, String course, AttemptBoundary before, int limit) {
        avoidNestedLoops();
        var c=facts(scope,"id=:learner"); var p=new HashMap<>(c.params());
        p.put("learner",learner); p.put("course",course);
        String boundary="";
        if(before != null) {
            p.put("time",before.time()); p.put("id",before.id());
            boundary=" and (taken_at<:time or (taken_at=:time and id<:id))";
        }
        return maps(c.sql()+ATTEMPT_ROWS+"where user_id=:learner and course_id=:course"+boundary+" order by taken_at desc,id desc limit "+(limit+1),p);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean attemptVisible(Scope scope, String attemptId) {
        avoidNestedLoops();
        var c=facts(scope); var p=new HashMap<>(c.params());
        p.put("attempt",attemptId);
        return !maps(c.sql()+ATTEMPT_ROWS+"where id=:attempt limit 1",p).isEmpty();
    }
}
