package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.repository.OrganizationUnitRepository;
import com.bidv.coretraining.security.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Bounded administration reads. Legacy endpoints remain for existing consumers. */
@Service
@Transactional(readOnly = true)
public class AdminListQueryService {
    public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {}
    public record NumberedPage<T>(List<T> items, long total, int page, int pageCount) {}
    public record ModerationPage(List<LessonReportDto> items, String nextCursor, boolean hasMore, long newCount) {}
    public record CourseChoice(String id, String code, String name) {}
    public record RoleSummary(String id, String code, String name, String description, boolean active,
            boolean protectedRole, boolean learningEnabled, boolean learnerRole, boolean assignedCoursesOnly, Long version,
            Set<String> permissions, Set<String> unitIds, Set<Long> userIds, long memberCount) {}
    public record RoleMemberRow(Long id, String username, String fullName, String unitId, Set<String> roles, boolean member, boolean selectable) {}
    private record Boundary(LocalDateTime time, long id) {}
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private final EntityManager em;
    private final AuthorizationService auth;
    private final OrganizationUnitRepository units;
    public AdminListQueryService(EntityManager em, AuthorizationService auth, OrganizationUnitRepository units) {
        this.em = em; this.auth = auth; this.units = units;
    }
    private void size(int size) { if (size < 1 || size > 100) throw new BadRequestException("Phân trang không hợp lệ"); }
    private void page(int page, int size) { size(size); if (page < 1 || page > 1_000_000) throw new BadRequestException("Phân trang không hợp lệ"); }
    private String search(String text) {
        text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (text.length() > 256) throw new BadRequestException("Từ khóa tối đa 256 ký tự");
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
    private Predicate text(CriteriaBuilder cb, String pattern, Expression<String>... fields) {
        return cb.or(Arrays.stream(fields).map(f -> cb.like(cb.lower(cb.coalesce(f, "")), pattern, '\\')).toArray(Predicate[]::new));
    }
    private List<String> scope(User actor, String permission, List<OrganizationUnit> hierarchy) {
        return hierarchy.stream().filter(u -> AccessPolicy.allows(actor, permission, u)).map(OrganizationUnit::getId).toList();
    }
    private Predicate in(CriteriaBuilder cb, Expression<?> value, Collection<?> ids) { return ids.isEmpty() ? cb.disjunction() : value.in(ids); }
    private Predicate unitScope(CriteriaBuilder cb, Path<?> unit, User actor, String permission, List<OrganizationUnit> hierarchy) {
        return AccessPolicy.system(actor) ? cb.conjunction() : in(cb, unit.get("id"), scope(actor, permission, hierarchy));
    }
    /** Keep role permissions paired with that same role's assigned/owner/program scope. */
    private Predicate courseScope(CriteriaBuilder cb, From<?, Course> course, User actor, String permission, List<OrganizationUnit> hierarchy) {
        if (AccessPolicy.system(actor)) return cb.conjunction();
        var owner = course.join("ownerUnit", JoinType.LEFT);
        if (!actor.isDynamicAccessInitialized()) {
            var activeIds = hierarchy.stream().filter(AccessPolicy::activeUnit).map(OrganizationUnit::getId).toList();
            var activeOwner = cb.or(cb.isNull(owner.get("id")), in(cb, owner.get("id"), activeIds));
            var parts = new ArrayList<Predicate>();
            if (actor.hasRole("ROLE_UNIT_ADMIN")) parts.add(in(cb, owner.get("id"), hierarchy.stream().filter(u -> AccessPolicy.inScope(actor, u)).map(OrganizationUnit::getId).toList()));
            if (actor.hasRole("ROLE_INSTRUCTOR")) parts.add(cb.isMember(actor.getId(), course.get("instructorIds")));
            return cb.and(activeOwner, cb.or(parts.toArray(Predicate[]::new)));
        }
        var parts = new ArrayList<Predicate>();
        var program = course.join("program", JoinType.LEFT);
        for (var role : AccessPolicy.policies(actor)) {
            if (!role.active() || role.learnerRole() || !role.permissions().contains(permission)) continue;
            if (role.assignedCoursesOnly()) parts.add(cb.isMember(actor.getId(), course.get("instructorIds")));
            else {
                var ids = hierarchy.stream().filter(u -> AccessPolicy.roleScope(role, u)).map(OrganizationUnit::getId).toList();
                if (role.legacyOwnerScope()) parts.add(in(cb, owner.get("id"), ids));
                else {
                    // EXISTS avoids multiplying a bank/report row for multiple matching program units.
                    var sq = cb.createQuery().subquery(String.class);
                    var p = sq.from(TrainingProgram.class); var pu = p.join("units");
                    sq.select(p.get("id")).where(cb.equal(p.get("id"), program.get("id")), in(cb, pu.get("id"), ids));
                    parts.add(cb.exists(sq));
                }
            }
        }
        return cb.or(parts.toArray(Predicate[]::new));
    }
    private Predicate nonSystem(CriteriaBuilder cb, Root<User> user, CriteriaQuery<?> query) {
        var roleQuery = query.subquery(Long.class); var u = roleQuery.from(User.class); var role = u.join("assignedRoles");
        roleQuery.select(u.get("id")).where(cb.equal(u.get("id"), user.get("id")), cb.isTrue(role.get("active")), cb.isTrue(role.get("protectedRole")));
        var legacyAdmin = cb.or(cb.isMember("ROLE_ADMIN", user.get("roles")), cb.and(cb.isEmpty(user.get("roles")), cb.equal(user.get("role"), "ROLE_ADMIN")));
        return cb.not(cb.or(cb.and(cb.isTrue(user.get("dynamicAccessInitialized")), cb.exists(roleQuery)), cb.and(cb.isFalse(user.get("dynamicAccessInitialized")), legacyAdmin)));
    }
    private String context(Object... values) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.toString(values).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Boundary boundary(String cursor, String context) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            if (cursor.length() > 512) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !parts[2].equals(context)) throw new IllegalArgumentException();
            long id = Long.parseLong(parts[1]); if (id < 1) throw new IllegalArgumentException();
            return new Boundary(LocalDateTime.parse(parts[0]), id);
        } catch (RuntimeException e) { throw new BadRequestException("Con trỏ phân trang không hợp lệ; hãy tải lại danh sách"); }
    }
    private String cursor(LocalDateTime time, Long id, String context) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(((time == null ? EPOCH : time) + "|" + id + "|" + context).getBytes(StandardCharsets.UTF_8));
    }
    private Predicate before(CriteriaBuilder cb, Expression<LocalDateTime> time, Expression<Long> id, Boundary boundary) {
        if (boundary == null) return cb.conjunction();
        return cb.or(cb.lessThan(time, boundary.time()), cb.and(cb.equal(time, boundary.time()), cb.lessThan(id, boundary.id())));
    }
    public CursorPage<BankQuestionDto> bank(String query, String status, String difficulty, String cursor, int size) {
        size(size); auth.requireOperation("exams", "read"); var actor = auth.current(); var hierarchy = units.findAll();
        String pattern = search(query), ctx = context("bank", pattern, status, difficulty, size);
        var boundary = boundary(cursor, ctx); var cb = em.getCriteriaBuilder(); var cq = cb.createQuery(BankQuestion.class); var q = cq.from(BankQuestion.class);
        var course = q.<BankQuestion, Course>join("course"); var lesson = q.join("relatedLesson", JoinType.LEFT);
        var domain = course.join("program", JoinType.LEFT).join("domain", JoinType.LEFT);
        var predicates = new ArrayList<Predicate>();
        predicates.add(courseScope(cb, course, actor, "exams:read", hierarchy));
        predicates.add(text(cb, pattern, q.get("code"), q.get("text"), lesson.get("title"), course.get("name"), domain.get("name")));
        if (status != null && !status.isBlank()) predicates.add(cb.equal(q.get("status"), enumValue(BankQuestion.Status.class, status)));
        if (difficulty != null && !difficulty.isBlank()) predicates.add(cb.equal(q.get("difficulty"), enumValue(BankQuestion.Difficulty.class, difficulty)));
        Expression<LocalDateTime> time = cb.coalesce(q.get("updatedAt"), EPOCH);
        predicates.add(before(cb, time, q.get("id"), boundary));
        cq.select(q).where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(time), cb.desc(q.get("id")));
        var rows = em.createQuery(cq).setMaxResults(size + 1).getResultList(); boolean more = rows.size() > size;
        var visible = rows.stream().limit(size).toList(); var last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        return new CursorPage<>(visible.stream().map(r -> bankDto(r, actor)).toList(), more ? cursor(last.getUpdatedAt(), last.getId(), ctx) : null, more);
    }
    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException e) { throw new BadRequestException("Bộ lọc không hợp lệ"); }
    }
    private BankQuestionDto bankDto(BankQuestion q, User actor) {
        var c = q.getCourse(); var d = c.getProgram() == null ? null : c.getProgram().getDomain();
        return new BankQuestionDto(q.getId(), q.getVersion(), q.getCode(), c.getId(), c.getName(), q.getRelatedLesson() == null ? null : q.getRelatedLesson().getId(),
                q.getRelatedLesson() == null ? null : q.getRelatedLesson().getTitle(), d == null ? null : d.getId(), d == null ? null : d.getName(), q.getText(), q.getType(),
                List.copyOf(q.getOptions()), List.copyOf(q.getCorrectAnswers()), q.getExplanation(), q.getDifficulty(), q.getStatus(), q.getUpdatedAt(),
                java.util.stream.Stream.of("read", "create", "update").filter(a -> AccessPolicy.allowsCourse(actor, "exams:" + a, c)).toList());
    }
    private <T> NumberedPage<T> numbered(Class<T> type, int page, int size, BiFunction<Root<T>, CriteriaQuery<?>, Predicate> filter, String order) {
        page(page, size); var cb = em.getCriteriaBuilder(); var countQuery = cb.createQuery(Long.class); var countRoot = countQuery.from(type);
        countQuery.select(cb.count(countRoot)).where(filter.apply(countRoot, countQuery)); long total = em.createQuery(countQuery).getSingleResult();
        var cq = cb.createQuery(type); var root = cq.from(type);
        cq.select(root).where(filter.apply(root, cq)).orderBy(cb.asc(cb.lower(root.get(order))), cb.asc(root.get("id")));
        var items = em.createQuery(cq).setFirstResult((page - 1) * size).setMaxResults(size).getResultList();
        return new NumberedPage<>(items, total, page, Math.max(1, (int) Math.ceil((double) total / size)));
    }
    public NumberedPage<UserDto> users(String query, String unitId, String status, int page, int size) {
        auth.permission("USER_MANAGE"); var actor = auth.current(); var hierarchy = units.findAll(); var cb = em.getCriteriaBuilder(); var pattern = search(query);
        var result = numbered(User.class, page, size, (u, cq) -> {
            var unit = u.join("organizationUnit", JoinType.LEFT);
            var predicates = new ArrayList<Predicate>(); predicates.add(unitScope(cb, unit, actor, "users:read", hierarchy));
            if (!AccessPolicy.system(actor)) predicates.add(nonSystem(cb, u, cq));
            predicates.add(text(cb, pattern, u.get("username"), u.get("fullName"), u.get("employeeId"), u.get("email")));
            if (unitId != null && !unitId.isBlank()) predicates.add(cb.equal(unit.get("id"), unitId));
            if (status != null && !status.isBlank()) predicates.add(cb.equal(cb.coalesce(u.get("status"), "ACTIVE"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        }, "fullName");
        return new NumberedPage<>(result.items().stream().map(u -> userDto(u, actor)).toList(), result.total(), result.page(), result.pageCount());
    }
    private UserDto userDto(User user, User actor) {
        return UserDto.builder().id(user.getId()).username(user.getUsername()).fullName(user.getFullName()).avatarColor(user.getAvatarColor()).role(user.getRole())
                .roles(user.effectiveRoles()).roleNames(AccessPolicy.roleNames(user)).permissions(AccessPolicy.permissions(user))
                .allowedActions(java.util.stream.Stream.of("create", "read", "update", "delete").filter(a -> AccessPolicy.allows(actor, "users:" + a, user.getOrganizationUnit())).collect(Collectors.toSet()))
                .unitId(user.getOrganizationUnit() == null ? null : user.getOrganizationUnit().getId()).scopeUnitIds(user.getScopeUnitIds()).status(user.getStatus()).learnerType(user.getLearnerType())
                .points(user.getPoints()).userGroupIds(user.groupIds()).groupNames(user.groupNames()).employeeId(user.getEmployeeId()).email(user.getEmail()).build();
    }
    public NumberedPage<AccessManagementService.GroupDto> groups(String query, String unitId, int page, int size) {
        auth.permission("GROUP_MANAGE"); var actor = auth.current(); var hierarchy = units.findAll(); var cb = em.getCriteriaBuilder(); String pattern = search(query);
        var result = numbered(UserGroup.class, page, size, (g, cq) -> {
            var unit = g.join("organizationUnit", JoinType.LEFT);
            return cb.and(unitScope(cb, unit, actor, "groups:read", hierarchy), text(cb, pattern, g.get("id"), g.get("name")),
                    unitId == null || unitId.isBlank() ? cb.conjunction() : cb.equal(unit.get("id"), unitId));
        }, "name");
        Map<String, Long> counts = new HashMap<>();
        if (!result.items().isEmpty()) em.createQuery("select g.id, count(u) from User u join u.userGroups g where g.id in :ids group by g.id", Object[].class)
                .setParameter("ids", result.items().stream().map(UserGroup::getId).toList()).getResultList().forEach(row -> counts.put((String) row[0], (Long) row[1]));
        var items = result.items().stream().map(g -> new AccessManagementService.GroupDto(g.getId(), g.getName(), g.getOrganizationUnit() == null ? null : g.getOrganizationUnit().getId(),
                g.getMandatoryCourses().stream().map(Course::getId).collect(Collectors.toSet()), java.util.stream.Stream.of("create", "read", "update", "delete")
                .filter(a -> AccessPolicy.allows(actor, "groups:" + a, g.getOrganizationUnit())).collect(Collectors.toSet()), counts.getOrDefault(g.getId(), 0L))).toList();
        return new NumberedPage<>(items, result.total(), result.page(), result.pageCount());
    }
    public NumberedPage<RoleSummary> roles(String query, String status, int page, int size, boolean managementOnly) {
        auth.requireOperation("roles", "read"); var actor = auth.current(); var hierarchy = units.findAll(); var cb = em.getCriteriaBuilder(); String pattern = search(query);
        // Roles are small configuration objects; resolve delegability once, then
        // count/filter/order/page the authorized IDs before member counts or DTO mapping.
        var allowed = em.createQuery("select r from AccessRole r", AccessRole.class).getResultList().stream()
                .filter(r -> delegable(r, actor, hierarchy)).map(AccessRole::getId).toList();
        var result = numbered(AccessRole.class, page, size, (r, cq) -> cb.and(in(cb, r.get("id"), allowed),
                text(cb, pattern, r.get("name"), r.get("code")),
                managementOnly ? cb.or(cb.isNull(r.get("learnerRole")), cb.isFalse(r.get("learnerRole"))) : cb.conjunction(),
                status == null || status.isBlank() ? cb.conjunction() : cb.equal(r.get("active"), "active".equals(status))), "name");
        Map<String, Long> counts = new HashMap<>();
        if (!result.items().isEmpty()) em.createQuery("select r.id, count(u) from User u join u.assignedRoles r where r.id in :ids group by r.id", Object[].class)
                .setParameter("ids", result.items().stream().map(AccessRole::getId).toList()).getResultList().forEach(row -> counts.put((String) row[0], (Long) row[1]));
        return new NumberedPage<>(result.items().stream().map(r -> new RoleSummary(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isActive(), r.isProtectedRole(),
                r.isLearningEnabled(), r.isLearnerRole(), r.isAssignedCoursesOnly(), r.getVersion(), Set.copyOf(r.getPermissions()), Set.copyOf(r.getUnitIds()), Set.of(), counts.getOrDefault(r.getId(), 0L))).toList(),
                result.total(), result.page(), result.pageCount());
    }
    public RoleSummary roleOption(String id) {
        auth.requireOperation("permissions", "read"); var actor = auth.current(); var role = em.find(AccessRole.class, id);
        if (role == null) throw new ResourceNotFoundException("Role");
        AccessPolicy.require(!role.isLearnerRole() && delegable(role, actor, units.findAll()));
        long count = em.createQuery("select count(u) from User u join u.assignedRoles r where r.id = :id", Long.class).setParameter("id", id).getSingleResult();
        return new RoleSummary(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isActive(), role.isProtectedRole(), role.isLearningEnabled(), role.isLearnerRole(), role.isAssignedCoursesOnly(), role.getVersion(),
                Set.copyOf(role.getPermissions()), Set.copyOf(role.getUnitIds()), Set.of(), count);
    }
    private boolean delegable(AccessRole r, User actor, List<OrganizationUnit> hierarchy) {
        if (AccessPolicy.system(actor)) return true;
        if (r.isProtectedRole() || r.isAssignedCoursesOnly()) return false;
        if ((r.isLearnerRole() || r.isLearningEnabled()) && !AccessPolicy.has(actor, "LEARN")) return false;
        String permission = AdminOperation.current("roles", "read").permission();
        return r.getPermissions().stream().allMatch(p -> AccessPolicy.has(actor, p)) && r.getUnitIds().stream().allMatch(id -> {
            var unit = hierarchy.stream().filter(u -> u.getId().equals(id)).findFirst().orElseThrow(() -> new ResourceNotFoundException("Unit"));
            return AccessPolicy.allows(actor, permission, unit) && r.getPermissions().stream().allMatch(p -> AccessPolicy.allows(actor, p, unit));
        });
    }
    public NumberedPage<RoleMemberRow> roleMembers(String roleId, String query, int page, int size) {
        auth.requireOperation("roles", "read"); var actor = auth.current(); var hierarchy = units.findAll(); var cb = em.getCriteriaBuilder(); String pattern = search(query);
        var role = em.find(AccessRole.class, roleId);
        if (role == null) throw new ResourceNotFoundException("Role");
        AccessPolicy.require(delegable(role, actor, hierarchy));
        var result = numbered(User.class, page, size, (u, cq) -> cb.and(
                unitScope(cb, u.join("organizationUnit", JoinType.LEFT), actor, "roles:update", hierarchy),
                AccessPolicy.system(actor) ? cb.conjunction() : nonSystem(cb, u, cq), text(cb, pattern, u.get("fullName"), u.get("username"))), "fullName");
        return new NumberedPage<>(result.items().stream().map(u -> new RoleMemberRow(u.getId(), u.getUsername(), u.getFullName(),
                u.getOrganizationUnit() == null ? null : u.getOrganizationUnit().getId(), u.effectiveRoles(), u.getAssignedRoles().stream().anyMatch(r -> r.getId().equals(roleId)),
                AccessPolicy.system(actor) || !Objects.equals(actor.getId(), u.getId()))).toList(), result.total(), result.page(), result.pageCount());
    }
    public NumberedPage<CourseChoice> groupCourses(String query, int page, int size) {
        auth.requireOperation("groups", "read"); var actor = auth.current(); var hierarchy = units.findAll(); var cb = em.getCriteriaBuilder(); String pattern = search(query);
        var result = numbered(Course.class, page, size, (c, cq) -> cb.and(courseScope(cb, c, actor, "groups:read", hierarchy), text(cb, pattern, c.get("id"), c.get("code"), c.get("name"))), "name");
        return new NumberedPage<>(result.items().stream().map(c -> new CourseChoice(c.getId(), c.getCode(), c.getName())).toList(), result.total(), result.page(), result.pageCount());
    }
    public CursorPage<EnrollmentRequestDto> enrollments(String query, String status, String cursor, int size) {
        size(size); auth.permission("ENROLLMENT_MANAGE"); var actor = auth.current(); var hierarchy = units.findAll();
        String pattern = search(query), ctx = context("enrollments", pattern, status, size); var boundary = boundary(cursor, ctx);
        var activeIds = hierarchy.stream().filter(AccessPolicy::activeUnit).map(OrganizationUnit::getId).toList();
        var scopeIds = scope(actor, "enrollments:read", hierarchy);
        if (!AccessPolicy.system(actor) && scopeIds.isEmpty()) return new CursorPage<>(List.of(), null, false);
        Map<String,Object> params = new HashMap<>();
        String active = activeIds.isEmpty() ? "1=0" : "id in (:activeUnits)";
        if (!activeIds.isEmpty()) params.put("activeUnits", activeIds);
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
        if (!AccessPolicy.system(actor)) { sql += " and u.unit_id in (:scopeUnits)"; params.put("scopeUnits", scopeIds); }
        sql += " and (lower(coalesce(u.username,'')) like :query escape '\\' or lower(coalesce(u.full_name,'')) like :query escape '\\' or lower(coalesce(c.name,'')) like :query escape '\\')";
        params.put("query", pattern);
        if (status != null && !status.isBlank()) { enumValue(EnrollmentStatus.class, status); sql += " and er.status=:status"; params.put("status", status); }
        if (boundary != null) { sql += " and (er.requested_at<:beforeTime or (er.requested_at=:beforeTime and er.id<:beforeId))"; params.put("beforeTime", boundary.time()); params.put("beforeId", boundary.id()); }
        sql += " order by er.requested_at desc, er.id desc";
        var nativeQuery = em.createNativeQuery(sql, CourseEnrollmentRequest.class); params.forEach(nativeQuery::setParameter);
        @SuppressWarnings("unchecked") List<CourseEnrollmentRequest> rows = nativeQuery.setMaxResults(size + 1).getResultList();
        boolean more = rows.size() > size; var visible = rows.stream().limit(size).toList(); var last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        var items = visible.stream().map(r -> EnrollmentRequestDto.builder().id(r.getId()).userId(r.getUser().getId()).username(r.getUser().getUsername()).fullName(r.getUser().getFullName())
                .courseId(r.getCourse().getId()).courseName(r.getCourse().getName()).status(r.getStatus()).requestedAt(r.getRequestedAt()).reviewedAt(r.getReviewedAt()).reviewedBy(r.getReviewedBy()).rejectionReason(r.getRejectionReason()).build()).toList();
        return new CursorPage<>(items, more ? cursor(last.getRequestedAt(), last.getId(), ctx) : null, more);
    }
    private static String legacyRole(String role) {
        return "(exists(select 1 from user_roles lr where lr.user_id=u.id and lr.role='" + role + "') or (not exists(select 1 from user_roles lr where lr.user_id=u.id) and u.role='" + role + "'))";
    }
    /** AccessPolicy.visible for the request's learner, independently of the reviewing actor. */
    private static String enrollmentVisibleSql() {
        String system = "((u.dynamic_access_initialized=true and exists(select 1 from user_access_roles ur join access_roles ar on ar.id=ur.role_id where ur.user_id=u.id and ar.active=true and ar.protected_role=true)) or (u.dynamic_access_initialized=false and " + legacyRole("ROLE_ADMIN") + "))";
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
                """ + "(ar.assigned_courses_only=true and " + assigned + ") or " + """
                    (ar.assigned_courses_only=false and ((ar.legacy_owner_scope=true and
                      exists(select 1 from unit_ancestors ut join access_role_units ru on ru.unit_id=ut.ancestor_id
                        where ru.role_id=ar.id and ut.unit_id=c.owner_unit_id))
                      or (ar.legacy_owner_scope=false and exists(select 1 from training_program_units pu
                        join unit_ancestors ut on ut.unit_id=pu.unit_id join access_role_units ru on ru.unit_id=ut.ancestor_id
                        where pu.program_id=c.program_id and ru.role_id=ar.id))))))
                """;
        String administrative = "(u.status='ACTIVE' and (" + system + " or " + activeUnit + ") and (" + system +
                " or (u.dynamic_access_initialized=true and " + dynamicScope + ") or (u.dynamic_access_initialized=false and " + activeOwner +
                " and ((" + legacyRole("ROLE_UNIT_ADMIN") + " and " + legacyScope + ") or (" + legacyRole("ROLE_INSTRUCTOR") + " and " + assigned + ")))))";
        String grants = """
                (exists(select 1 from user_registered_courses rc where rc.user_id=u.id and rc.course_id=c.id)
                  or exists(select 1 from course_unit_grants cg join unit_ancestors ut on ut.ancestor_id=cg.unit_id where cg.course_id=c.id and ut.unit_id=u.unit_id)
                  or exists(select 1 from program_unit_grants pg join unit_ancestors ut on ut.ancestor_id=pg.unit_id where pg.program_id=c.program_id and ut.unit_id=u.unit_id)
                  or exists(select 1 from domain_unit_grants dg join unit_ancestors ut on ut.ancestor_id=dg.unit_id where dg.domain_id=p.domain_id and ut.unit_id=u.unit_id)
                  or exists(select 1 from training_program_units pu join unit_ancestors ut on ut.ancestor_id=pu.unit_id where pu.program_id=c.program_id and ut.unit_id=u.unit_id)
                  or exists(select 1 from training_domain_units du join unit_ancestors ut on ut.ancestor_id=du.unit_id where du.domain_id=p.domain_id and ut.unit_id=u.unit_id)
                  or exists(select 1 from unit_ancestors ut where ut.unit_id=u.unit_id and ut.ancestor_id=c.owner_unit_id))
                """;
        return "(" + administrative + " or (u.unit_id is null and c.owner_unit_id is null) or (u.unit_id is not null and " + activeUnit + " and " + grants + "))";
    }
    public ModerationPage reports(String query, String status, String cursor, int size) {
        size(size); auth.requireOperation("courses", "read"); var actor = auth.current(); var hierarchy = units.findAll(); var cb = em.getCriteriaBuilder(); String pattern = search(query);
        String ctx = context("reports", pattern, status, size); var boundary = boundary(cursor, ctx);
        var cq = cb.createQuery(LessonReport.class); var r = cq.from(LessonReport.class); var lesson = r.<LessonReport, Lesson>join("lesson"); var course = lesson.<Lesson, Course>join("course");
        Expression<LocalDateTime> time = cb.coalesce(r.get("createdAt"), EPOCH);
        var predicates = new ArrayList<Predicate>(); predicates.add(courseScope(cb, course, actor, "courses:read", hierarchy));
        predicates.add(text(cb, pattern, lesson.get("title"), course.get("name"), r.join("reporter").get("fullName")));
        if (status != null && !status.isBlank()) predicates.add(cb.equal(r.get("status"), enumValue(LessonReport.Status.class, status)));
        predicates.add(before(cb, time, r.get("id"), boundary));
        cq.select(r).where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(time), cb.desc(r.get("id")));
        var rows = em.createQuery(cq).setMaxResults(size + 1).getResultList(); boolean more = rows.size() > size; var visible = rows.stream().limit(size).toList(); var last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        var count = cb.createQuery(Long.class); var root = count.from(LessonReport.class);
        count.select(cb.count(root)).where(courseScope(cb, root.<LessonReport, Lesson>join("lesson").join("course"), actor, "courses:read", hierarchy), cb.equal(root.get("status"), LessonReport.Status.NEW));
        return new ModerationPage(visible.stream().map(row -> reportDto(row, actor)).toList(), more ? cursor(last.getCreatedAt(), last.getId(), ctx) : null, more, em.createQuery(count).getSingleResult());
    }
    public LessonReportDto report(Long id) {
        auth.requireOperation("courses", "read"); var actor = auth.current(); var report = em.find(LessonReport.class, id);
        if (report == null) throw new ResourceNotFoundException("LessonReport");
        AccessPolicy.require(AccessPolicy.canReadCourse(actor, report.getLesson().getCourse())); return reportDto(report, actor);
    }
    private LessonReportDto reportDto(LessonReport r, User actor) {
        return new LessonReportDto(r.getId(), r.getVersion(), r.getLesson().getId(), r.getLesson().getTitle(), r.getLesson().getCourse().getId(), r.getLesson().getCourse().getName(), r.getReporter().getFullName(), r.getContent(),
                java.util.stream.IntStream.range(0, r.getImages().size()).mapToObj(i -> "/api/lesson-reports/" + r.getId() + "/images/" + i).toList(), r.getStatus(), r.getResolution(), r.getReviewer() == null ? null : r.getReviewer().getFullName(),
                r.getCreatedAt(), r.getReviewedAt(), AccessPolicy.allowsCourse(actor, "courses:update", r.getLesson().getCourse()));
    }
}
