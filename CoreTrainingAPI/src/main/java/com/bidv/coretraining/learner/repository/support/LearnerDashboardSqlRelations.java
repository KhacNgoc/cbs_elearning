package com.bidv.coretraining.learner.repository.support;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import java.text.Normalizer;
@org.springframework.stereotype.Component
public class LearnerDashboardSqlRelations {
    private final NamedParameterJdbcTemplate jdbc;
    public LearnerDashboardSqlRelations(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Context(String sql,Map<String,Object> params) {}
    private static String published(String alias) {
        return "(" + alias + ".status is null or upper(" + alias + ".status) in ('ACTIVE','PUBLISHED'))";
    }

    private static String in(String expression, Collection<?> values, String name, Map<String, Object> p) {
        if (values.isEmpty())
            return "1=0";
        p.put(name, values);
        return expression + " in (:" + name + ")";
    }

    /**
     * The same unpaged visible relation is reusable for assessment histories
     * (including archived courses).
     */
    public Context visible(LearnerQueryScope u) {
        Map<String, Object> p = new HashMap<>();
        p.put("uid", u.id());
        record Unit(String id, String parent, boolean active) {
        }
        Map<String, Unit> units = new HashMap<>();
        jdbc.query("select id,parent_id,active from organization_units", Map.of(), r -> {
            String id = r.getString(1);
            units.put(id, new Unit(id, r.getString(2), r.getBoolean(3)));
        });
        java.util.function.Function<String, Set<String>> ancestors = id -> {
            Set<String> seen = new HashSet<>();
            String n = id;
            while (n != null) {
                var unit = units.get(n);
                if (unit == null || !unit.active() || !seen.add(n))
                    return Set.of();
                n = unit.parent();
            }
            return seen;
        };
        String actorUnit = u.actorUnit();
        Set<String> own = ancestors.apply(actorUnit);
        java.util.function.Function<Set<String>, Set<String>> descendants = scope -> {
            Set<String> result = new HashSet<>();
            units.keySet().forEach(id -> {
                if (ancestors.apply(id).stream().anyMatch(scope::contains))
                    result.add(id);
            });
            return result;
        };
        String registered = "exists(select 1 from user_registered_courses rc where rc.user_id=:uid and rc.course_id=c.id)";
        List<String> admin = new ArrayList<>(), editor = new ArrayList<>();
        if (u.active() && (u.system() || actorUnit == null || !own.isEmpty())) {
            if (u.system())
                admin.add("1=1");
            else if (u.dynamic()) {
                int i = 0;
                for (var role : u.roles()) {
                    String rule;
                    // Course-allocation scope is added below for every role that is not limited to assigned courses.
                    if (role.assignedCoursesOnly())
                        rule = "exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=:uid)";
                    else {
                        String ids = "role" + (i++);
                        var scope = descendants.apply(role.unitIds());
                        rule = role.legacyOwnerScope() ? in("c.owner_unit_id", scope, ids, p)
                                : "exists(select 1 from training_program_units pu where pu.program_id=c.program_id and "
                                        + in("pu.unit_id", scope, ids, p) + ")";
                        rule = "(" + rule + " or exists(select 1 from course_unit_grants acg where acg.course_id=c.id and "
                                + in("acg.unit_id", scope, ids, p) + "))";
                    }
                    admin.add(rule);
                    if (role.editor())
                        editor.add(rule);
                }
            } else {
                var scope = u.scopeUnitIds().isEmpty() ? (actorUnit == null ? Set.<String>of() : Set.of(actorUnit))
                        : u.scopeUnitIds();
                if (u.unitAdmin())
                    admin.add(in("c.owner_unit_id", descendants.apply(scope), "legacyScope", p));
                if (u.instructor()) {
                    var activeOwners = units.keySet().stream().filter(id -> !ancestors.apply(id).isEmpty()).toList();
                    admin.add("((c.owner_unit_id is null or " + in("c.owner_unit_id", activeOwners, "activeOwners", p)
                            + ") and exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=:uid))");
                }
            }
        }
        if (u.system() || !u.dynamic())
            editor.addAll(admin);
        String editorRule = editor.isEmpty() ? "1=0" : "(" + String.join(" or ", editor) + ")";
        List<String> allow = new ArrayList<>(admin);
        if (actorUnit == null || !own.isEmpty())
            allow.add(registered);
        if (!own.isEmpty())
            allow.add("exists(select 1 from course_unit_grants g where g.course_id=c.id and "
                    + in("g.unit_id", own, "ownUnits", p) + ")");
        return new Context("with visible as (select c.*,p.name program_name,p.domain_id,d.name domain_name,case when "
                + editorRule
                + " then 1 else 0 end editor_access from courses c join training_programs p on p.id=c.program_id join training_domains d on d.id=p.domain_id where "
                + (allow.isEmpty() ? "1=0" : "(" + String.join(" or ", allow) + ")") + ")", p);
    }

    /**
     * Published courses the actor edits or, with the learn permission, is assigned or registered in. Decides what
     * requireCourse decides one course at a time; FlashcardAccessParityTest holds the two together.
     */
    public Context learnable(LearnerQueryScope u, boolean learner) {
        var v = visible(u);
        Map<String, Object> p = new HashMap<>(v.params());
        String enrolled = !learner ? "" : " or exists(select 1 from user_registered_courses rc where rc.user_id=:uid"
                + " and rc.course_id=c.id) or " + mandatory(u, p);
        return new Context(v.sql() + " select c.id,c.name,c.editor_access from visible c"
                + " where c.status='PUBLISHED' and (c.editor_access=1" + enrolled + ")", p);
    }

    /** Expects alias c with program_id and domain_id, as the visible relation provides. */
    private static String mandatory(LearnerQueryScope u, Map<String, Object> p) {
        Set<String> own = u.grantUnits();
        return "exists(select 1 from course_unit_grants cg where cg.course_id=c.id and "
                + in("cg.unit_id", own, "grantUnits", p) + " and upper(cg.grant_type)='MANDATORY')";
    }

    public Context facts(LearnerQueryScope u) {
        var v = visible(u);
        Map<String, Object> p = new HashMap<>(v.params());
        String mandatory = mandatory(u, p);
        String sql = v.sql()
                + """
                        , lessons_visible as (
                          select l.id,l.course_id,l.chapter_id,
                            case when exists(select 1 from user_completed_lessons cl where cl.user_id=:uid and cl.lesson_id=l.id) then 1 else 0 end done,
                            case when exists(select 1 from quizzes q where q.lesson_id=l.id and q.required_for_progress=true
                                and exists(select 1 from questions qu where qu.quiz_id=q.id)
                                and not exists(select 1 from user_quiz_scores qs where qs.user_id=:uid and qs.lesson_id=l.id and qs.total>0 and floor(qs.score*100.0/qs.total+0.5)>=q.passing_score)) then 1 else 0 end quiz_missing,
                            (select max(lp.updated_at) from lesson_progress lp where lp.user_id=:uid and lp.lesson_id=l.id) accessed
                          from lessons l join chapters ch on ch.id=l.chapter_id where
                        """
                + published("l") + " and " + published("ch") + """
                        ), base as (
                          select c.*,
                        """ + "case when " + mandatory + " then 1 else 0 end mandatory,"
                + """
                            case when exists(select 1 from user_registered_courses rc where rc.user_id=:uid and rc.course_id=c.id) then 1 else 0 end registered,
                            (select count(*) from lessons_visible l where l.course_id=c.id) total_count,
                            (select count(*) from lessons_visible l where l.course_id=c.id and l.done=1) completed_count,
                            (select max(l.accessed) from lessons_visible l where l.course_id=c.id) last_accessed_at,
                            case when exists(select 1 from user_course_deadlines dd where dd.user_id=:uid and dd.course_id=c.id) then (select dd.due_date from user_course_deadlines dd where dd.user_id=:uid and dd.course_id=c.id) else c.completion_deadline end due_date,
                            case when exists(select 1 from chapters ch where ch.course_id=c.id and
                        """
                + published("ch") + """
                            ) and not exists(select 1 from chapters ch where ch.course_id=c.id and
                        """ + published("ch")
                + """
                            and (not exists(select 1 from lessons_visible l where l.chapter_id=ch.id) or exists(select 1 from lessons_visible l where l.chapter_id=ch.id and (l.done=0 or l.quiz_missing=1)))) then 1 else 0 end complete
                          from visible c where
                        """
                + published("c")
                + """
                        ), sources as (
                          select b.*,case when mandatory=1 then 'ASSIGNED' when registered=1 then 'REGISTERED'
                            when exists(select 1 from course_enrollment_requests er where er.user_id=:uid and er.course_id=b.id and er.status='PENDING') then 'PENDING' else 'AVAILABLE' end source
                          from base b
                        ), facts as (
                          select s.*,case when source in ('PENDING','AVAILABLE') then source when complete=1 then 'COMPLETED'
                            when last_accessed_at is not null or completed_count>0
                              or exists(select 1 from quiz_attempts qa join quizzes q on q.id=qa.quiz_id join lessons l on l.id=q.lesson_id where qa.user_id=:uid and qa.total>0 and l.course_id=s.id)
                              or exists(select 1 from user_quiz_scores qs where qs.user_id=:uid and qs.course_id=s.id)
                            then 'IN_PROGRESS' else 'NOT_STARTED' end learning_status
                          from sources s
                        )
                        """;
        return new Context(sql, p);
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.forLanguageTag("vi")), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd');
    }

    public static String normalizedSql(String value) {
        String from = "àáạảãăằắặẳẵâầấậẩẫèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
        String to = "a".repeat(17) + "e".repeat(11) + "i".repeat(5) + "o".repeat(17) + "u".repeat(11) + "y".repeat(5)
                + "d";
        String sql = "translate(normalize(lower(" + value + "), NFC),'" + from + "','" + to + "')";
        for (char mark : new char[] { '\u0300', '\u0301', '\u0303', '\u0309', '\u0323', '\u0306', '\u0302', '\u031b' })
            sql = "replace(" + sql + ",'" + mark + "','')";
        return sql;
    }

    // Vietnamese alphabet primary order: a, ă, â, b, c, d, đ, e, ê, ... o, ô, ơ,
    // ... u, ư.
    // Explicit translation avoids relying on the database host's default collation.
    public static String vietnameseOrderSql(String expression) {
        String[] groups = { "aàáạảã", "ăằắặẳẵ", "âầấậẩẫ", "b", "c", "d", "đ", "eèéẹẻẽ", "êềếệểễ", "f", "g", "h",
                "iìíịỉĩ", "j", "k", "l", "m", "n", "oòóọỏõ", "ôồốộổỗ", "ơờớợởỡ", "p", "q", "r", "s", "t", "uùúụủũ",
                "ưừứựửữ", "v", "w", "x", "yỳýỵỷỹ", "z" };
        StringBuilder from = new StringBuilder(), to = new StringBuilder();
        for (int i = 0; i < groups.length; i++) {
            from.append(groups[i]);
            to.append(String.valueOf((char) ('A' + i)).repeat(groups[i].length()));
        }
        return "translate(normalize(lower(" + expression + "), NFC),'" + from + "','" + to + "') collate \"C\"";
    }

    public static String vietnameseToneSql(String expression) {
        String[] groups = { "aàáạảã", "ăằắặẳẵ", "âầấậẩẫ", "eèéẹẻẽ", "êềếệểễ", "iìíịỉĩ", "oòóọỏõ", "ôồốộổỗ", "ơờớợởỡ",
                "uùúụủũ", "ưừứựửữ", "yỳýỵỷỹ" };
        String from = "bcdđfghjklmnpqrstvwxz", to = "A".repeat(from.length());
        for (var group : groups) {
            from += group;
            to += "ABEFCD";
        }
        return "translate(normalize(lower(" + expression + "), NFC),'" + from + "','" + to + "') collate \"C\"";
    }

}
