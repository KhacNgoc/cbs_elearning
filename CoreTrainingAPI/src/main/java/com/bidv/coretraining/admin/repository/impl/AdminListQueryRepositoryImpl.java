package com.bidv.coretraining.admin.repository.impl;
import com.bidv.coretraining.access.entity.AccessRole;
import com.bidv.coretraining.admin.dto.AdminListQueryDto.*;
import com.bidv.coretraining.admin.repository.AdminListQueryRepository;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.entity.CourseEnrollmentRequest;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.entity.LessonReport;
import com.bidv.coretraining.quiz.entity.BankQuestion;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
@Repository
public class AdminListQueryRepositoryImpl implements AdminListQueryRepository {
 private final EntityManager em;
 public AdminListQueryRepositoryImpl(EntityManager em) { this.em=em; }
 private static final LocalDateTime EPOCH = LocalDateTime.of(1970,1,1,0,0);
    private Predicate text(CriteriaBuilder cb, String pattern, Expression<String>... fields) {
        return cb.or(Arrays.stream(fields).map(f -> cb.like(cb.lower(cb.coalesce(f, "")), pattern, '\\'))
                .toArray(Predicate[]::new));
    }
    private Predicate in(CriteriaBuilder cb, Expression<?> value, Collection<?> ids) {
        return ids.isEmpty() ? cb.disjunction() : value.in(ids);
    }
    private Predicate nonSystem(CriteriaBuilder cb, Root<User> user, CriteriaQuery<?> query) {
        var roleQuery = query.subquery(Long.class);
        var u = roleQuery.from(User.class);
        var role = u.join("assignedRoles");
        roleQuery.select(u.get("id")).where(cb.equal(u.get("id"), user.get("id")), cb.isTrue(role.get("active")),
                cb.isTrue(role.get("protectedRole")));
        var legacyAdmin = cb.or(cb.isMember("ROLE_ADMIN", user.get("roles")),
                cb.and(cb.isEmpty(user.get("roles")), cb.equal(user.get("role"), "ROLE_ADMIN")));
        return cb.not(cb.or(cb.and(cb.isTrue(user.get("dynamicAccessInitialized")), cb.exists(roleQuery)),
                cb.and(cb.isFalse(user.get("dynamicAccessInitialized")), legacyAdmin)));
    }
    private Predicate before(CriteriaBuilder cb, Expression<LocalDateTime> time, Expression<Long> id,
            Boundary boundary) {
        if (boundary == null)
            return cb.conjunction();
        return cb.or(cb.lessThan(time, boundary.time()),
                cb.and(cb.equal(time, boundary.time()), cb.lessThan(id, boundary.id())));
    }
    private <T> NumberedPage<T> numbered(Class<T> type, int page, int size,
            BiFunction<Root<T>, CriteriaQuery<?>, Predicate> filter, String order) {
        var cb = em.getCriteriaBuilder();
        var countQuery = cb.createQuery(Long.class);
        var countRoot = countQuery.from(type);
        countQuery.select(cb.count(countRoot)).where(filter.apply(countRoot, countQuery));
        long total = em.createQuery(countQuery).getSingleResult();
        var cq = cb.createQuery(type);
        var root = cq.from(type);
        cq.select(root).where(filter.apply(root, cq)).orderBy(cb.asc(cb.lower(root.get(order))),
                cb.asc(root.get("id")));
        var items = em.createQuery(cq).setFirstResult((page - 1) * size).setMaxResults(size).getResultList();
        return new NumberedPage<>(items, total, page, Math.max(1, (int) Math.ceil((double) total / size)));
    }
    private static String legacyRole(String role) {
        return "(exists(select 1 from user_roles lr where lr.user_id=u.id and lr.role='" + role
                + "') or (not exists(select 1 from user_roles lr where lr.user_id=u.id) and u.role='" + role + "'))";
    }
    private static String enrollmentVisibleSql() {
        String system = "((u.dynamic_access_initialized=true and exists(select 1 from user_access_roles ur join access_roles ar on ar.id=ur.role_id where ur.user_id=u.id and ar.active=true and ar.protected_role=true)) or (u.dynamic_access_initialized=false and "
                + legacyRole("ROLE_ADMIN") + "))";
        String activeUnit = "(u.unit_id is null or exists(select 1 from unit_ancestors ut where ut.unit_id=u.unit_id))";
        String activeOwner = "(c.owner_unit_id is null or exists(select 1 from unit_ancestors ut where ut.unit_id=c.owner_unit_id))";
        String assigned = "exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=u.id)";
        String legacyScope = """
                exists(select 1 from unit_ancestors ut where ut.unit_id=c.owner_unit_id and
                  (exists(select 1 from user_scope_units us where us.user_id=u.id and us.unit_id=ut.ancestor_id)
                    or (not exists(select 1 from user_scope_units us where us.user_id=u.id) and ut.ancestor_id=u.unit_id)))
                """;
        String dynamicScope = """
                exists(select 1 from user_access_roles ur join access_roles ar on ar.id=ur.role_id
                  join access_role_permissions ap on ap.role_id=ar.id
                  where ur.user_id=u.id and ar.active=true and (ar.learner_role is null or ar.learner_role=false)
                    and ap.permission in ('courses:read','courses:update') and (
                """ + "(ar.assigned_courses_only=true and " + assigned + ") or "
                + """
                            (ar.assigned_courses_only=false and (exists(select 1 from course_unit_grants acg
                                join unit_ancestors ut on ut.unit_id=acg.unit_id join access_role_units ru on ru.unit_id=ut.ancestor_id
                                where acg.course_id=c.id and ru.role_id=ar.id)
                              or (ar.legacy_owner_scope=true and
                              exists(select 1 from unit_ancestors ut join access_role_units ru on ru.unit_id=ut.ancestor_id
                                where ru.role_id=ar.id and ut.unit_id=c.owner_unit_id))
                              or (ar.legacy_owner_scope=false and exists(select 1 from training_program_units pu
                                join unit_ancestors ut on ut.unit_id=pu.unit_id join access_role_units ru on ru.unit_id=ut.ancestor_id
                                where pu.program_id=c.program_id and ru.role_id=ar.id))))))
                        """;
        String administrative = "(u.status='ACTIVE' and (" + system + " or " + activeUnit + ") and (" + system +
                " or (u.dynamic_access_initialized=true and " + dynamicScope
                + ") or (u.dynamic_access_initialized=false and " + activeOwner +
                " and ((" + legacyRole("ROLE_UNIT_ADMIN") + " and " + legacyScope + ") or ("
                + legacyRole("ROLE_INSTRUCTOR") + " and " + assigned + ")))))";
        String registered = "exists(select 1 from user_registered_courses rc where rc.user_id=u.id and rc.course_id=c.id)";
        String grants = "(" + registered + " or exists(select 1 from course_unit_grants cg join unit_ancestors ut"
                + " on ut.ancestor_id=cg.unit_id where cg.course_id=c.id and ut.unit_id=u.unit_id))";
        return "(" + administrative
                + " or (u.unit_id is null and " + registered + ") or (u.unit_id is not null and " + activeUnit
                + " and " + grants + "))";
    }
    private Predicate unitScope(CriteriaBuilder cb, Path<?> unit, Scope scope) {
        return scope.system() ? cb.conjunction() : in(cb, unit.get("id"), scope.unitIds());
    }
    private Predicate courseScope(CriteriaBuilder cb, From<?, Course> course, Scope scope) {
        if(scope.system()) return cb.conjunction();
        var owner=course.join("ownerUnit",JoinType.LEFT);
        var parts=new ArrayList<Predicate>();
        var program=scope.legacy() ? null : course.join("program",JoinType.LEFT);
        for(var part:scope.courseParts()) {
            if(part.assigned()) parts.add(cb.isMember(scope.actorId(),course.get("instructorIds")));
            else if(part.owner()) parts.add(in(cb,owner.get("id"),part.unitIds()));
            else {
                var sq=cb.createQuery().subquery(String.class);
                var p=sq.from(TrainingProgram.class); var pu=p.join("units");
                sq.select(p.get("id")).where(cb.equal(p.get("id"),program.get("id")),in(cb,pu.get("id"),part.unitIds()));
                parts.add(cb.exists(sq));
            }
        }
        var allowed=cb.or(parts.toArray(Predicate[]::new));
        return scope.legacy() ? cb.and(cb.or(cb.isNull(owner.get("id")),in(cb,owner.get("id"),scope.activeUnitIds())),allowed) : allowed;
    }
    public Rows<BankQuestion> bank(String pattern, BankQuestion.Status status, BankQuestion.Difficulty difficulty, Boundary boundary, int size, Scope scope) {
        var cb = em.getCriteriaBuilder();
        var cq = cb.createQuery(BankQuestion.class);
        var q = cq.from(BankQuestion.class);
        var course = q.<BankQuestion, Course>join("course");
        var lesson = q.join("relatedLesson", JoinType.LEFT);
        var domain = course.join("program", JoinType.LEFT).join("domain", JoinType.LEFT);
        var predicates = new ArrayList<Predicate>();
        predicates.add(courseScope(cb, course, scope));
        predicates.add(text(cb, pattern, q.get("code"), q.get("text"), lesson.get("title"), course.get("name"),
                domain.get("name")));
        if (status != null)
            predicates.add(cb.equal(q.get("status"), status));
        if (difficulty != null)
            predicates.add(cb.equal(q.get("difficulty"), difficulty));
        Expression<LocalDateTime> time = cb.coalesce(q.get("updatedAt"), EPOCH);
        predicates.add(before(cb, time, q.get("id"), boundary));
        cq.select(q).where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(time), cb.desc(q.get("id")));
        var rows = em.createQuery(cq).setMaxResults(size + 1).getResultList();
        boolean more = rows.size() > size;
        var visible = rows.stream().limit(size).toList();
        return new Rows<>(visible,more);
    }
    public NumberedPage<User> users(String pattern, String unitId, String status, int page, int size, Scope scope) {
        var cb = em.getCriteriaBuilder();
        var result = numbered(User.class, page, size, (u, cq) -> {
            var unit = u.join("organizationUnit", JoinType.LEFT);
            var predicates = new ArrayList<Predicate>();
            predicates.add(unitScope(cb, unit, scope));
            if (!scope.system())
                predicates.add(nonSystem(cb, u, cq));
            predicates
                    .add(text(cb, pattern, u.get("username"), u.get("fullName"), u.get("employeeId"), u.get("email")));
            if (unitId != null && !unitId.isBlank())
                predicates.add(cb.equal(unit.get("id"), unitId));
            if (status != null && !status.isBlank())
                predicates.add(cb.equal(cb.coalesce(u.get("status"), "ACTIVE"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        }, "fullName");
        return result;
    }
    public NumberedPage<AccessRole> roles(String pattern, String status, int page, int size, boolean managementOnly, List<String> allowed) {
        var cb = em.getCriteriaBuilder();
        var result = numbered(AccessRole.class, page, size, (r, cq) -> cb.and(in(cb, r.get("id"), allowed),
                text(cb, pattern, r.get("name"), r.get("code")),
                managementOnly ? cb.or(cb.isNull(r.get("learnerRole")), cb.isFalse(r.get("learnerRole")))
                        : cb.conjunction(),
                status == null || status.isBlank() ? cb.conjunction()
                        : cb.equal(r.get("active"), "active".equals(status))),
                "name");
        return result;
    }
    public NumberedPage<User> roleMembers(String pattern, int page, int size, Scope scope) {
        var cb = em.getCriteriaBuilder();
        var result = numbered(User.class, page, size, (u, cq) -> cb.and(
                unitScope(cb, u.join("organizationUnit", JoinType.LEFT), scope),
                scope.system() ? cb.conjunction() : nonSystem(cb, u, cq),
                text(cb, pattern, u.get("fullName"), u.get("username"))), "fullName");
        return result;
    }
    public Rows<CourseEnrollmentRequest> enrollments(String pattern, String status, Boundary boundary, int size, Scope scope) {
        var activeIds = scope.activeUnitIds();
        var scopeIds = scope.unitIds();
        if (!scope.system() && scopeIds.isEmpty())
            return new Rows<>(List.of(), false);
        Map<String, Object> params = new HashMap<>();
        String active = activeIds.isEmpty() ? "1=0" : "id in (:activeUnits)";
        if (!activeIds.isEmpty())
            params.put("activeUnits", activeIds);
        String sql = """
                with recursive unit_ancestors(unit_id, ancestor_id) as (
                  select id, id from organization_units where
                """ + active + """
                  union all select tree.unit_id, n.parent_id from unit_ancestors tree
                    join organization_units n on n.id=tree.ancestor_id where n.parent_id is not null
                )
                select er.* from course_enrollment_requests er join users u on u.id=er.user_id
                  join courses c on c.id=er.course_id left join training_programs p on p.id=c.program_id
                where
                """ + enrollmentVisibleSql();
        if (!scope.system()) {
            sql += " and u.unit_id in (:scopeUnits)";
            params.put("scopeUnits", scopeIds);
        }
        sql += " and (lower(coalesce(u.username,'')) like :query escape '\\' or lower(coalesce(u.full_name,'')) like :query escape '\\' or lower(coalesce(c.name,'')) like :query escape '\\')";
        params.put("query", pattern);
        if (status != null && !status.isBlank()) {
            sql += " and er.status=:status";
            params.put("status", status);
        }
        if (boundary != null) {
            sql += " and (er.requested_at<:beforeTime or (er.requested_at=:beforeTime and er.id<:beforeId))";
            params.put("beforeTime", boundary.time());
            params.put("beforeId", boundary.id());
        }
        sql += " order by er.requested_at desc, er.id desc";
        var nativeQuery = em.createNativeQuery(sql, CourseEnrollmentRequest.class);
        params.forEach(nativeQuery::setParameter);
        @SuppressWarnings("unchecked")
        List<CourseEnrollmentRequest> rows = nativeQuery.setMaxResults(size + 1).getResultList();
        boolean more = rows.size() > size;
        var visible = rows.stream().limit(size).toList();
        return new Rows<>(visible,more);
    }
    public ModerationRows reports(String pattern, LessonReport.Status status, Boundary boundary, int size, Scope scope) {
        var cb = em.getCriteriaBuilder();
        var cq = cb.createQuery(LessonReport.class);
        var r = cq.from(LessonReport.class);
        var lesson = r.<LessonReport, Lesson>join("lesson");
        var course = lesson.<Lesson, Course>join("course");
        Expression<LocalDateTime> time = cb.coalesce(r.get("createdAt"), EPOCH);
        var predicates = new ArrayList<Predicate>();
        predicates.add(courseScope(cb, course, scope));
        predicates.add(text(cb, pattern, lesson.get("title"), course.get("name"), r.join("reporter").get("fullName")));
        if (status != null)
            predicates.add(cb.equal(r.get("status"), status));
        predicates.add(before(cb, time, r.get("id"), boundary));
        cq.select(r).where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(time), cb.desc(r.get("id")));
        var rows = em.createQuery(cq).setMaxResults(size + 1).getResultList();
        boolean more = rows.size() > size;
        var visible = rows.stream().limit(size).toList();
        var count = cb.createQuery(Long.class);
        var root = count.from(LessonReport.class);
        count.select(cb.count(root)).where(courseScope(cb, root.<LessonReport, Lesson>join("lesson").join("course"),
                scope), cb.equal(root.get("status"), LessonReport.Status.NEW));
        return new ModerationRows(new Rows<>(visible,more),em.createQuery(count).getSingleResult());
    }

    public List<AccessRole> allRoles() { return em.createQuery("select r from AccessRole r",AccessRole.class).getResultList(); }
    public AccessRole role(String id) { return em.find(AccessRole.class,id); }
    public LessonReport report(Long id) { return em.find(LessonReport.class,id); }
    public Map<String,Long> roleCounts(List<String> ids) { return counts("select r.id, count(u) from User u join u.assignedRoles r where r.id in :ids group by r.id",ids); }
    private Map<String,Long> counts(String query,List<String> ids) {
        Map<String,Long> counts=new HashMap<>();
        if(!ids.isEmpty()) em.createQuery(query,Object[].class).setParameter("ids",ids).getResultList().forEach(row->counts.put((String)row[0],(Long)row[1]));
        return counts;
    }
}
