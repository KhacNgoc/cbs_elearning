package com.bidv.coretraining.repository.impl;
import com.bidv.coretraining.repository.support.LearnerDashboardSqlRelations;
import com.bidv.coretraining.repository.support.LearnerQueryScope;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import java.time.*;
import com.bidv.coretraining.dto.CourseDto;
import com.bidv.coretraining.dto.LearnerDashboardDto.*;
import com.bidv.coretraining.repository.LearnerDashboardQueryRepository;
import static com.bidv.coretraining.repository.support.LearnerDashboardSqlRelations.*;
@org.springframework.stereotype.Repository
public class LearnerDashboardQueryRepositoryImpl implements LearnerDashboardQueryRepository {
 private final NamedParameterJdbcTemplate jdbc;
 private final LearnerDashboardSqlRelations relations;
 public LearnerDashboardQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc,LearnerDashboardSqlRelations relations){this.jdbc=jdbc;this.relations=relations;}
    public Summary summary(LearnerQueryScope u) {
        var c = relations.facts(u);
        return jdbc.queryForObject(
                c.sql() + """
                          select sum(case when source in ('ASSIGNED','REGISTERED') then 1 else 0 end) joined,
                          sum(case when source in ('AVAILABLE','PENDING') then 1 else 0 end) discover,
                          sum(case when learning_status='NOT_STARTED' then 1 else 0 end) not_started,
                          sum(case when learning_status='IN_PROGRESS' then 1 else 0 end) in_progress,
                          sum(case when learning_status='COMPLETED' then 1 else 0 end) completed,
                          sum(mandatory) mandatory,sum(case when mandatory=1 and learning_status='COMPLETED' then 1 else 0 end) mandatory_complete,
                          sum(case when source in ('ASSIGNED','REGISTERED') then completed_count else 0 end) done,
                          sum(case when source in ('ASSIGNED','REGISTERED') then total_count else 0 end) total from facts
                        """,
                c.params(),
                (r, n) -> new Summary(r.getLong("joined"), r.getLong("discover"), r.getLong("not_started"),
                        r.getLong("in_progress"), r.getLong("completed"), r.getLong("mandatory"),
                        r.getLong("mandatory_complete"), r.getLong("done"), r.getLong("total"),
                        r.getLong("total") == 0 ? null
                                : (int) Math.round(r.getLong("done") * 100.0 / r.getLong("total"))));
    }

    public Page page(LearnerQueryScope u, Filter f) {
        var c = relations.facts(u);
        var p = new HashMap<>(c.params());
        String where = " where source "
                + ("discover".equals(f.tab()) ? "in ('AVAILABLE','PENDING')" : "in ('ASSIGNED','REGISTERED')");
        String[][] filters = { { "learning_status", f.status() }, { "domain_id", f.domainId() },
                { "business_module", f.businessModule() }, { "program_id", f.programId() }, { "level", f.level() },
                { "source", f.source() } };
        for (int i = 0; i < filters.length; i++)
            if (filters[i][1] != null && !filters[i][1].isBlank()) {
                where += " and " + filters[i][0] + "=:f" + i;
                p.put("f" + i, filters[i][1]);
            }
        if (f.mandatory())
            where += " and mandatory=1";
        if (f.search() != null && !f.search().isBlank()) {
            where += " and " + normalizedSql(
                    "coalesce(name,'') || ' ' || coalesce(code,'') || ' ' || coalesce(description,'') || ' ' || coalesce(keywords,'')")
                    + " like :search escape '!'";
            p.put("search",
                    "%" + normalize(f.search().trim()).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long count = jdbc.queryForObject(c.sql() + " select count(*) from facts" + where, p, Long.class);
        int size = Math.max(1, Math.min(f.size(), 60)), page = Math.max(1, f.page());
        p.put("limit", size);
        p.put("offset", (long) (page - 1) * size);
        String order = switch (f.sort() == null ? "" : f.sort()) {
            case "name" ->
                vietnameseOrderSql("name") + "," + vietnameseToneSql("name") + ",normalize(lower(name), NFC),id";
            case "recent" -> "last_accessed_at desc nulls last,id";
            default ->
                "case when learning_status<>'COMPLETED' and source in ('ASSIGNED','REGISTERED') then due_date else null end asc nulls last,last_accessed_at desc nulls last,id";
        };
        var items = jdbc.query(c.sql()
                + " select id,code,name,thumbnail,icon_name,program_id,program_name,domain_id,domain_name,business_module,level,source,mandatory,learning_status,completed_count,total_count,due_date,last_accessed_at from facts"
                + where + " order by " + order + " limit :limit offset :offset", p, (r, n) -> {
                    var dto = CourseDto.builder().id(r.getString("id")).code(r.getString("code"))
                            .name(r.getString("name")).thumbnail(r.getString("thumbnail"))
                            .iconName(r.getString("icon_name")).programId(r.getString("program_id"))
                            .programName(r.getString("program_name")).domainId(r.getString("domain_id"))
                            .domainName(r.getString("domain_name")).businessModule(r.getString("business_module"))
                            .level(r.getString("level")).build();
                    int done = r.getInt("completed_count"), total = r.getInt("total_count");
                    return new Card(dto, r.getString("source"), r.getInt("mandatory") == 1,
                            r.getString("learning_status"), done, total,
                            total == 0 ? 0 : (int) Math.round(done * 100.0 / total),
                            r.getObject("due_date", LocalDate.class),
                            r.getObject("last_accessed_at", LocalDateTime.class), null);
                });
        return new Page(items, count, page, size);
    }

    public Map<String, List<Choice>> choices(LearnerQueryScope u, String tab) {
        var c = relations.facts(u);
        String where = " where source "
                + ("discover".equals(tab) ? "in ('AVAILABLE','PENDING')" : "in ('ASSIGNED','REGISTERED')");
        Map<String, List<Choice>> result = new LinkedHashMap<>();
        List<String> selects = new ArrayList<>();
        for (var entry : Map.of("status", "learning_status", "source", "source", "domainId", "domain_id", "programId",
                "program_id", "businessModule", "business_module", "level", "level").entrySet()) {
            result.put(entry.getKey(), new ArrayList<>());
            String column = entry.getValue(), label = column.equals("domain_id") ? "domain_name"
                    : column.equals("program_id") ? "program_name" : column;
            selects.add("select distinct '" + entry.getKey() + "' choice_key," + column + " choice_value," + label
                    + " choice_label from facts" + where + " and " + column + " is not null");
        }
        jdbc.query(c.sql() + String.join(" union all ", selects) + " order by choice_key,choice_value", c.params(),
                r -> {
                    result.get(r.getString("choice_key"))
                            .add(new Choice(r.getString("choice_value"), r.getString("choice_label")));
                });
        return result;
    }

    public String resumeCourseId(LearnerQueryScope u) {
        var c = relations.facts(u);
        var ids = jdbc.query(c.sql()
                + " select id from facts where source in ('ASSIGNED','REGISTERED') and learning_status<>'COMPLETED' and total_count>0 order by last_accessed_at desc nulls last,id limit 1",
                c.params(), (r, n) -> r.getString(1));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static final String JOINED_COURSES = " select id,name from visible c where (editor_access=1 or exists(select 1 from facts f where f.id=c.id and f.source in ('ASSIGNED','REGISTERED')))";

    public List<CourseChoice> courseChoices(LearnerQueryScope u, String search, int size) {
        var c = relations.facts(u);
        var p = new HashMap<>(c.params());
        p.put("limit", Math.max(1, Math.min(size, 100)));
        p.put("choiceSearch", "%" + normalize(search == null ? "" : search.trim()).replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%");
        return jdbc.query(c.sql() + JOINED_COURSES + " and "
                + normalizedSql("name || ' ' || code") + " like :choiceSearch escape '!' order by "
                + normalizedSql("name") + ",id limit :limit", p,
                (r, n) -> new CourseChoice(r.getString(1), r.getString(2)));
    }

    /** Same visibility as {@link #courseChoices}, unfiltered, for cross-course assistant retrieval. */
    public List<CourseChoice> accessibleCourses(LearnerQueryScope u, int limit) {
        var c = relations.facts(u);
        var p = new HashMap<>(c.params());
        p.put("limit", Math.max(1, Math.min(limit, 2000)));
        return jdbc.query(c.sql() + JOINED_COURSES + " order by id limit :limit", p,
                (r, n) -> new CourseChoice(r.getString(1), r.getString(2)));
    }

    public boolean canResume(LearnerQueryScope u,String id) {
        var ctx=relations.facts(u);var params=new HashMap<>(ctx.params());params.put("courseId",id);
        var count=jdbc.queryForObject(ctx.sql()+" select count(*) from facts where id=:courseId and source in ('ASSIGNED','REGISTERED')",params,Integer.class);
        return count!=null && count>0;
    }
    public ResumeFacts resumeFacts(Long uid,String cid) {
        var params=Map.<String,Object>of("uid",uid,"cid",cid);
        var completed=jdbc.query("select cl.lesson_id from user_completed_lessons cl join lessons l on l.id=cl.lesson_id where cl.user_id=:uid and l.course_id=:cid",params,(r,n)->r.getString(1));
        var scores=jdbc.query("select lesson_id,score,total from user_quiz_scores where user_id=:uid and course_id=:cid",params,(r,n)->new ResumeScore(r.getString(1),r.getInt(2),r.getInt(3)));
        return new ResumeFacts(completed,scores);
    }
}
