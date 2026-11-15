package com.bidv.coretraining.learner.repository.impl;
import com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations;
import com.bidv.coretraining.learner.repository.support.LearnerQueryScope;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import java.time.*;
import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.learner.dto.LearnerDashboardDto.*;
import com.bidv.coretraining.learner.repository.LearnerDashboardQueryRepository;
import static com.bidv.coretraining.learner.repository.support.LearnerDashboardSqlRelations.*;
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
        // The total comes with the page (count over the filtered rows) so facts is evaluated once.
        long[] count = { 0 };
        var items = jdbc.query(c.sql()
                + " select id,code,name,thumbnail,icon_name,program_id,program_name,domain_id,domain_name,business_module,level,source,mandatory,learning_status,completed_count,total_count,due_date,last_accessed_at,count(*) over() total_rows from facts"
                + where + " order by " + order + " limit :limit offset :offset", p, (r, n) -> {
                    count[0] = r.getLong("total_rows");
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
        // A page past the end has no row to carry the total.
        if (items.isEmpty() && page > 1)
            count[0] = jdbc.queryForObject(c.sql() + " select count(*) from facts" + where, p, Long.class);
        return new Page(items, count[0], page, size);
    }

    /**
     * Summary, filter choices and resume course of the dashboard from one evaluation of the facts relation.
     * The relation holds one row per visible published course, so it is aggregated here rather than
     * re-evaluated by three queries.
     */
    public OverviewFacts overview(LearnerQueryScope u, String tab) {
        var c = relations.facts(u);
        record Row(String id, String source, String status, boolean mandatory, long done, long total,
                LocalDateTime accessed, Map<String, Choice> choices) {}
        var rows = jdbc.query(c.sql()
                + " select id,source,learning_status,mandatory,completed_count,total_count,last_accessed_at,"
                + "domain_id,domain_name,program_id,program_name,business_module,level from facts",
                c.params(), (r, n) -> {
                    Map<String, Choice> choices = new LinkedHashMap<>();
                    choices.put("status", new Choice(r.getString("learning_status"), r.getString("learning_status")));
                    choices.put("source", new Choice(r.getString("source"), r.getString("source")));
                    choices.put("domainId", new Choice(r.getString("domain_id"), r.getString("domain_name")));
                    choices.put("programId", new Choice(r.getString("program_id"), r.getString("program_name")));
                    choices.put("businessModule", new Choice(r.getString("business_module"), r.getString("business_module")));
                    choices.put("level", new Choice(r.getString("level"), r.getString("level")));
                    return new Row(r.getString("id"), r.getString("source"), r.getString("learning_status"),
                            r.getInt("mandatory") == 1, r.getLong("completed_count"), r.getLong("total_count"),
                            r.getObject("last_accessed_at", LocalDateTime.class), choices);
                });
        Set<String> joinedSources = Set.of("ASSIGNED", "REGISTERED");
        long joined = 0, discover = 0, notStarted = 0, inProgress = 0, completed = 0, mandatory = 0,
                mandatoryComplete = 0, done = 0, total = 0;
        for (var row : rows) {
            boolean isJoined = joinedSources.contains(row.source());
            if (isJoined) {
                joined++;
                done += row.done();
                total += row.total();
            } else if ("AVAILABLE".equals(row.source()) || "PENDING".equals(row.source()))
                discover++;
            switch (row.status()) {
                case "NOT_STARTED" -> notStarted++;
                case "IN_PROGRESS" -> inProgress++;
                case "COMPLETED" -> completed++;
                default -> { }
            }
            if (row.mandatory()) {
                mandatory++;
                if ("COMPLETED".equals(row.status()))
                    mandatoryComplete++;
            }
        }
        var summary = new Summary(joined, discover, notStarted, inProgress, completed, mandatory, mandatoryComplete,
                done, total, total == 0 ? null : (int) Math.round(done * 100.0 / total));

        boolean discoverTab = "discover".equals(tab);
        var tabRows = rows.stream().filter(row -> joinedSources.contains(row.source()) != discoverTab).toList();
        Map<String, List<Choice>> choices = new LinkedHashMap<>();
        for (String key : List.of("status", "source", "domainId", "programId", "businessModule", "level"))
            choices.put(key, tabRows.stream().map(row -> row.choices().get(key)).filter(choice -> choice.value() != null)
                    .distinct().sorted(Comparator.comparing(Choice::value)).toList());

        String resume = rows.stream()
                .filter(row -> joinedSources.contains(row.source()) && !"COMPLETED".equals(row.status()) && row.total() > 0)
                .min(Comparator.comparing(Row::accessed, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(Row::id))
                .map(Row::id).orElse(null);
        return new OverviewFacts(summary, choices, resume);
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

    public List<LearnableCourseRow> learnableCourses(LearnerQueryScope u, boolean learner) {
        var c = relations.learnable(u, learner);
        return jdbc.query(c.sql(), c.params(),
                (r, n) -> new LearnableCourseRow(r.getString(1), r.getString(2), r.getInt(3) == 1));
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
