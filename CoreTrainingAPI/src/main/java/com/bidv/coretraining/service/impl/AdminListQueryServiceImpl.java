package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.enums.EnrollmentStatus;
import com.bidv.coretraining.dto.AccessManagementDto;
import com.bidv.coretraining.service.*;
import com.bidv.coretraining.dto.AdminListQueryDto.*;
import com.bidv.coretraining.repository.AdminListQueryRepository;
import com.bidv.coretraining.repository.AdminListQueryRepository.*;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.repository.OrganizationUnitRepository;
import com.bidv.coretraining.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bounded administration reads. Legacy endpoints remain for existing consumers.
 */
@Service
@Transactional(readOnly = true)
public class AdminListQueryServiceImpl implements AdminListQueryService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private final AdminListQueryRepository repository;
    private final AuthorizationService auth;
    private final OrganizationUnitRepository units;

    public AdminListQueryServiceImpl(AdminListQueryRepository repository, AuthorizationService auth, OrganizationUnitRepository units) {
        this.repository = repository;
        this.auth = auth;
        this.units = units;
    }

    private void size(int size) {
        if (size < 1 || size > 100)
            throw new BadRequestException("Phân trang không hợp lệ");
    }

    private void page(int page, int size) {
        size(size);
        if (page < 1 || page > 1_000_000)
            throw new BadRequestException("Phân trang không hợp lệ");
    }

    private String search(String text) {
        text = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (text.length() > 256)
            throw new BadRequestException("Từ khóa tối đa 256 ký tự");
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Resolve each role with its own permission and course scope before pagination. */
    private Scope authorizedScope(User actor, String permission, List<OrganizationUnit> hierarchy) {
        boolean system=AccessPolicy.system(actor);
        Set<String> active=hierarchy.stream().filter(AccessPolicy::activeUnit).map(OrganizationUnit::getId).collect(Collectors.toSet());
        List<CoursePart> parts=new ArrayList<>();
        if(!system && !actor.isDynamicAccessInitialized()) {
            if(actor.hasRole("ROLE_UNIT_ADMIN")) parts.add(new CoursePart(false,true,hierarchy.stream().filter(u->AccessPolicy.inScope(actor,u)).map(OrganizationUnit::getId).collect(Collectors.toSet())));
            if(actor.hasRole("ROLE_INSTRUCTOR")) parts.add(new CoursePart(true,false,Set.of()));
        } else if(!system) for(var role:AccessPolicy.policies(actor)) {
            if(!role.active() || role.learnerRole() || !role.permissions().contains(permission)) continue;
            parts.add(new CoursePart(role.assignedCoursesOnly(),role.legacyOwnerScope(),hierarchy.stream().filter(u->AccessPolicy.roleScope(role,u)).map(OrganizationUnit::getId).collect(Collectors.toSet())));
        }
        return new Scope(system,actor.getId(),new HashSet<>(scope(actor,permission,hierarchy)),!actor.isDynamicAccessInitialized(),active,parts);
    }

    private List<String> scope(User actor, String permission, List<OrganizationUnit> hierarchy) {
        return hierarchy.stream().filter(u -> AccessPolicy.allows(actor, permission, u)).map(OrganizationUnit::getId)
                .toList();
    }

    /**
     * Keep role permissions paired with that same role's assigned/owner/program
     * scope.
     */

    private String context(Object... values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Arrays.toString(values).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Boundary boundary(String cursor, String context) {
        if (cursor == null || cursor.isBlank())
            return null;
        try {
            if (cursor.length() > 512)
                throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !parts[2].equals(context))
                throw new IllegalArgumentException();
            long id = Long.parseLong(parts[1]);
            if (id < 1)
                throw new IllegalArgumentException();
            return new Boundary(LocalDateTime.parse(parts[0]), id);
        } catch (RuntimeException e) {
            throw new BadRequestException("Con trỏ phân trang không hợp lệ; hãy tải lại danh sách");
        }
    }

    private String cursor(LocalDateTime time, Long id, String context) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ((time == null ? EPOCH : time) + "|" + id + "|" + context).getBytes(StandardCharsets.UTF_8));
    }

    public CursorPage<BankQuestionDto> bank(String query, String status, String difficulty, String cursor, int size) {
        size(size);
        auth.requireOperation("exams", "read");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query), ctx = context("bank", pattern, status, difficulty, size);
        var boundary = boundary(cursor, ctx);
        var rows=repository.bank(pattern, status == null || status.isBlank() ? null : enumValue(BankQuestion.Status.class,status), difficulty == null || difficulty.isBlank() ? null : enumValue(BankQuestion.Difficulty.class,difficulty),boundary,size,authorizedScope(actor,"exams:read",hierarchy));
        boolean more=rows.hasMore(); var visible=rows.items();
        var last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        return new CursorPage<>(visible.stream().map(r -> bankDto(r, actor)).toList(),
                more ? cursor(last.getUpdatedAt(), last.getId(), ctx) : null, more);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Bộ lọc không hợp lệ");
        }
    }

    private BankQuestionDto bankDto(BankQuestion q, User actor) {
        var c = q.getCourse();
        var d = c.getProgram() == null ? null : c.getProgram().getDomain();
        return new BankQuestionDto(q.getId(), q.getVersion(), q.getCode(), c.getId(), c.getName(),
                q.getRelatedLesson() == null ? null : q.getRelatedLesson().getId(),
                q.getRelatedLesson() == null ? null : q.getRelatedLesson().getTitle(), d == null ? null : d.getId(),
                d == null ? null : d.getName(), q.getText(), q.getType(),
                List.copyOf(q.getOptions()), List.copyOf(q.getCorrectAnswers()), q.getExplanation(), q.getDifficulty(),
                q.getStatus(), q.getUpdatedAt(),
                java.util.stream.Stream.of("read", "create", "update")
                        .filter(a -> AccessPolicy.allowsCourse(actor, "exams:" + a, c)).toList());
    }

    public NumberedPage<UserDto> users(String query, String unitId, String status, int page, int size) {
        auth.permission("USER_MANAGE");
        var actor = auth.current();
        var hierarchy = units.findAll();
        var pattern = search(query);
        page(page,size);
        var result = repository.users(pattern, unitId, status, page, size, authorizedScope(actor,"users:read",hierarchy));
        return new NumberedPage<>(result.items().stream().map(u -> userDto(u, actor)).toList(), result.total(),
                result.page(), result.pageCount());
    }

    private UserDto userDto(User user, User actor) {
        return UserDto.builder().id(user.getId()).username(user.getUsername()).fullName(user.getFullName())
                .avatarColor(user.getAvatarColor()).role(user.getRole())
                .roles(user.effectiveRoles()).roleNames(AccessPolicy.roleNames(user))
                .permissions(AccessPolicy.permissions(user))
                .allowedActions(java.util.stream.Stream.of("create", "read", "update", "delete")
                        .filter(a -> AccessPolicy.allows(actor, "users:" + a, user.getOrganizationUnit()))
                        .collect(Collectors.toSet()))
                .unitId(user.getOrganizationUnit() == null ? null : user.getOrganizationUnit().getId())
                .scopeUnitIds(user.getScopeUnitIds()).status(user.getStatus()).learnerType(user.getLearnerType())
                .points(user.getPoints()).userGroupIds(user.groupIds()).groupNames(user.groupNames())
                .employeeId(user.getEmployeeId()).email(user.getEmail()).build();
    }

    public NumberedPage<AccessManagementDto.GroupDto> groups(String query, String unitId, int page, int size) {
        auth.permission("GROUP_MANAGE");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query);
        page(page,size);
        var result = repository.groups(pattern, unitId, page, size, authorizedScope(actor,"groups:read",hierarchy));
        var counts=repository.groupCounts(result.items().stream().map(UserGroup::getId).toList());
        var items = result.items().stream()
                .map(g -> new AccessManagementDto.GroupDto(g.getId(), g.getName(),
                        g.getOrganizationUnit() == null ? null : g.getOrganizationUnit().getId(),
                        g.getMandatoryCourses().stream().map(Course::getId).collect(Collectors.toSet()),
                        java.util.stream.Stream.of("create", "read", "update", "delete")
                                .filter(a -> AccessPolicy.allows(actor, "groups:" + a, g.getOrganizationUnit()))
                                .collect(Collectors.toSet()),
                        counts.getOrDefault(g.getId(), 0L)))
                .toList();
        return new NumberedPage<>(items, result.total(), result.page(), result.pageCount());
    }

    public NumberedPage<RoleSummary> roles(String query, String status, int page, int size, boolean managementOnly) {
        auth.requireOperation("roles", "read");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query);
        // Roles are small configuration objects; resolve delegability once, then
        // count/filter/order/page the authorized IDs before member counts or DTO
        // mapping.
        var allowed = repository.allRoles().stream()
                .filter(r -> delegable(r, actor, hierarchy)).map(AccessRole::getId).toList();
        page(page,size);
        var result = repository.roles(pattern, status, page, size, managementOnly, allowed);
        var counts=repository.roleCounts(result.items().stream().map(AccessRole::getId).toList());
        return new NumberedPage<>(
                result.items().stream()
                        .map(r -> new RoleSummary(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isActive(),
                                r.isProtectedRole(),
                                r.isLearningEnabled(), r.isLearnerRole(), r.isAssignedCoursesOnly(), r.getVersion(),
                                Set.copyOf(r.getPermissions()), Set.copyOf(r.getUnitIds()), Set.of(),
                                counts.getOrDefault(r.getId(), 0L)))
                        .toList(),
                result.total(), result.page(), result.pageCount());
    }

    public RoleSummary roleOption(String id) {
        auth.requireOperation("permissions", "read");
        var actor = auth.current();
        var role = repository.role(id);
        if (role == null)
            throw new ResourceNotFoundException("Role");
        AccessPolicy.require(!role.isLearnerRole() && delegable(role, actor, units.findAll()));
        long count=repository.roleCounts(List.of(id)).getOrDefault(id,0L);
        return new RoleSummary(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isActive(),
                role.isProtectedRole(), role.isLearningEnabled(), role.isLearnerRole(), role.isAssignedCoursesOnly(),
                role.getVersion(),
                Set.copyOf(role.getPermissions()), Set.copyOf(role.getUnitIds()), Set.of(), count);
    }

    private boolean delegable(AccessRole r, User actor, List<OrganizationUnit> hierarchy) {
        if (AccessPolicy.system(actor))
            return true;
        if (r.isProtectedRole() || r.isAssignedCoursesOnly())
            return false;
        if ((r.isLearnerRole() || r.isLearningEnabled()) && !AccessPolicy.has(actor, "LEARN"))
            return false;
        String permission = AdminOperation.current("roles", "read").permission();
        return r.getPermissions().stream().allMatch(p -> AccessPolicy.has(actor, p))
                && r.getUnitIds().stream().allMatch(id -> {
                    var unit = hierarchy.stream().filter(u -> u.getId().equals(id)).findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Unit"));
                    return AccessPolicy.allows(actor, permission, unit)
                            && r.getPermissions().stream().allMatch(p -> AccessPolicy.allows(actor, p, unit));
                });
    }

    public NumberedPage<RoleMemberRow> roleMembers(String roleId, String query, int page, int size) {
        auth.requireOperation("roles", "read");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query);
        var role = repository.role(roleId);
        if (role == null)
            throw new ResourceNotFoundException("Role");
        AccessPolicy.require(delegable(role, actor, hierarchy));
        page(page,size);
        var result = repository.roleMembers(pattern, page, size, authorizedScope(actor,"roles:update",hierarchy));
        return new NumberedPage<>(
                result.items().stream().map(u -> new RoleMemberRow(u.getId(), u.getUsername(), u.getFullName(),
                        u.getOrganizationUnit() == null ? null : u.getOrganizationUnit().getId(), u.effectiveRoles(),
                        u.getAssignedRoles().stream().anyMatch(r -> r.getId().equals(roleId)),
                        AccessPolicy.system(actor) || !Objects.equals(actor.getId(), u.getId()))).toList(),
                result.total(), result.page(), result.pageCount());
    }

    public NumberedPage<CourseChoice> groupCourses(String query, int page, int size) {
        auth.requireOperation("groups", "read");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query);
        page(page,size);
        var result = repository.groupCourses(pattern, page, size, authorizedScope(actor,"groups:read",hierarchy));
        return new NumberedPage<>(
                result.items().stream().map(c -> new CourseChoice(c.getId(), c.getCode(), c.getName())).toList(),
                result.total(), result.page(), result.pageCount());
    }

    public CursorPage<EnrollmentRequestDto> enrollments(String query, String status, String cursor, int size) {
        size(size);
        auth.permission("ENROLLMENT_MANAGE");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query), ctx = context("enrollments", pattern, status, size);
        var boundary = boundary(cursor, ctx);
        var scope=authorizedScope(actor,"enrollments:read",hierarchy);
        if (!scope.system() && scope.unitIds().isEmpty()) return new CursorPage<>(List.of(),null,false);
        if(status != null && !status.isBlank()) enumValue(EnrollmentStatus.class,status);
        var rows=repository.enrollments(pattern,status,boundary,size,scope);
        boolean more=rows.hasMore(); var visible=rows.items();
        var last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        var items = visible.stream()
                .map(r -> EnrollmentRequestDto.builder().id(r.getId()).userId(r.getUser().getId())
                        .username(r.getUser().getUsername()).fullName(r.getUser().getFullName())
                        .courseId(r.getCourse().getId()).courseName(r.getCourse().getName()).status(r.getStatus())
                        .requestedAt(r.getRequestedAt()).reviewedAt(r.getReviewedAt()).reviewedBy(r.getReviewedBy())
                        .rejectionReason(r.getRejectionReason()).build())
                .toList();
        return new CursorPage<>(items, more ? cursor(last.getRequestedAt(), last.getId(), ctx) : null, more);
    }

    public ModerationPage reports(String query, String status, String cursor, int size) {
        size(size);
        auth.requireOperation("courses", "read");
        var actor = auth.current();
        var hierarchy = units.findAll();
        String pattern = search(query);
        String ctx = context("reports", pattern, status, size);
        var boundary = boundary(cursor, ctx);
        var result=repository.reports(pattern,status == null || status.isBlank() ? null : enumValue(LessonReport.Status.class,status),boundary,size,authorizedScope(actor,"courses:read",hierarchy));
        boolean more=result.rows().hasMore(); var visible=result.rows().items();
        var last = visible.isEmpty() ? null : visible.get(visible.size() - 1);
        return new ModerationPage(visible.stream().map(row -> reportDto(row, actor)).toList(),
                more ? cursor(last.getCreatedAt(), last.getId(), ctx) : null, more,
                result.newCount());
    }

    public LessonReportDto report(Long id) {
        auth.requireOperation("courses", "read");
        var actor = auth.current();
        var report = repository.report(id);
        if (report == null)
            throw new ResourceNotFoundException("LessonReport");
        AccessPolicy.require(AccessPolicy.canReadCourse(actor, report.getLesson().getCourse()));
        return reportDto(report, actor);
    }

    private LessonReportDto reportDto(LessonReport r, User actor) {
        return new LessonReportDto(r.getId(), r.getVersion(), r.getLesson().getId(), r.getLesson().getTitle(),
                r.getLesson().getCourse().getId(), r.getLesson().getCourse().getName(), r.getReporter().getFullName(),
                r.getContent(),
                java.util.stream.IntStream.range(0, r.getImages().size())
                        .mapToObj(i -> "/api/lesson-reports/" + r.getId() + "/images/" + i).toList(),
                r.getStatus(), r.getResolution(), r.getReviewer() == null ? null : r.getReviewer().getFullName(),
                r.getCreatedAt(), r.getReviewedAt(),
                AccessPolicy.allowsCourse(actor, "courses:update", r.getLesson().getCourse()));
    }
}
