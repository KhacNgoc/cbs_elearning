package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.AccessManagementDto.*;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class AccessManagementServiceImpl implements AccessManagementService {

    private final PermissionCache cache;
    private final AuthorizationService auth;
    private final OrganizationUnitRepository units;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final CourseRepository courses;

    public AccessManagementServiceImpl(AuthorizationService auth, OrganizationUnitRepository units,
            UserGroupRepository groups, UserRepository users, CourseRepository courses,
            PermissionCache cache) {
        this.auth = auth;
        this.units = units;
        this.groups = groups;
        this.users = users;
        this.courses = courses;
        this.cache = cache;
    }

    private Set<String> actions(String page, OrganizationUnit n) {
        return java.util.stream.Stream.of("create", "read", "update", "delete")
                .filter(a -> AccessPolicy.allows(auth.current(), page + ":" + a, n))
                .collect(java.util.stream.Collectors.toSet());
    }

    public List<UnitDto> units() {
        auth.requireOperation("units", "read");
        return units.findAll().stream().filter(auth::allowedScope).map(this::dto).toList();
    }

    private UnitDto dto(OrganizationUnit u) {
        return new UnitDto(u.getId(), u.getName(), u.getType(), u.getParent() == null ? null : u.getParent().getId(),
                u.isActive(), actions("units", u));
    }

    private String generateUnitId() {
        for (int i = 0; i < 20; i++) {
            String candidate = "UNIT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            if (!units.existsById(candidate)) {
                return candidate;
            }
        }
        return "UNIT_" + System.currentTimeMillis();
    }

    public UnitDto saveUnit(String id, UnitDto r) {
        auth.permission("ORG_MANAGE");
        String unitId;
        if (id == null) {
            unitId = (r.id() == null || r.id().isBlank()) ? generateUnitId() : r.id().trim();
        } else {
            unitId = id;
        }
        String type = (r.type() == null || r.type().isBlank()) ? "UNIT" : r.type();
        if (unitId.isBlank() || r.name() == null || r.name().isBlank()
                || !Set.of("UNIT", "DEPARTMENT").contains(type))
            throw new BadRequestException("Mã, tên và loại đơn vị không hợp lệ");
        OrganizationUnit u = id == null ? OrganizationUnit.builder().id(unitId).build() : auth.unit(id);
        if (id == null && units.existsById(u.getId()))
            throw new BadRequestException("Mã đơn vị đã tồn tại");
        OrganizationUnit parent = r.parentId() == null || r.parentId().isBlank() ? null : auth.unit(r.parentId());
        if (AccessPolicy.ancestors(parent).contains(u.getId()))
            throw new BadRequestException("Cơ cấu đơn vị không được tạo vòng lặp");
        if (id != null)
            auth.scope(u);
        if (!AccessPolicy.system(auth.current())) {
            auth.scope(parent);
            AccessPolicy.require(
                    id == null || Objects.equals(u.getParent() == null ? null : u.getParent().getId(), r.parentId()));
        }
        u.setParent(parent);
        u.setName(r.name().trim());
        u.setType(type);
        u.setActive(r.active());
        cache.invalidateAfterCommit();
        return dto(units.save(u));
    }

    public List<GroupDto> groups() {
        auth.permission("GROUP_MANAGE");
        return groups.findAll().stream().filter(g -> auth.allowedScope(g.getOrganizationUnit())).map(this::dto)
                .toList();
    }

    private GroupDto dto(UserGroup g) {
        return new GroupDto(g.getId(), g.getName(),
                g.getOrganizationUnit() == null ? null : g.getOrganizationUnit().getId(),
                g.getMandatoryCourses().stream().map(Course::getId).collect(java.util.stream.Collectors.toSet()),
                actions("groups", g.getOrganizationUnit()), users.countByUserGroupsId(g.getId()));
    }

    private String generateGroupId() {
        for (int i = 0; i < 20; i++) {
            String candidate = "GRP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            if (!groups.existsById(candidate)) {
                return candidate;
            }
        }
        return "GRP_" + System.currentTimeMillis();
    }

    public GroupDto saveGroup(String id, GroupDto r) {
        auth.permission("GROUP_MANAGE");
        String groupId;
        if (id == null) {
            groupId = (r.id() == null || r.id().isBlank()) ? generateGroupId() : r.id().trim();
        } else {
            groupId = id;
        }
        if (groupId.isBlank() || r.name() == null || r.name().isBlank())
            throw new BadRequestException("Mã và tên nhóm không được để trống");
        UserGroup g = id == null ? UserGroup.builder().id(groupId).build()
                : groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group"));
        if (id == null && groups.existsById(g.getId()))
            throw new BadRequestException("Mã nhóm đã tồn tại");
        if (id != null)
            auth.scope(g.getOrganizationUnit());
        OrganizationUnit unit = r.unitId() == null ? null : auth.unit(r.unitId());
        auth.scope(unit);
        Set<Course> mandatory = new HashSet<>();
        if (r.mandatoryCourseIds() != null)
            for (String courseId : r.mandatoryCourseIds()) {
                Course c = courses.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Course"));
                User probe = User.builder().role("ROLE_USER").organizationUnit(unit).build();
                AccessPolicy.require(AccessPolicy.visible(probe, c));
                mandatory.add(c);
            }
        // Moving an existing group cannot silently move its members into a foreign
        // scope.
        if (id != null
                && !Objects.equals(g.getOrganizationUnit() == null ? null : g.getOrganizationUnit().getId(), r.unitId())
                && users.existsByUserGroupsId(id))
            throw new BadRequestException("Nhóm đang có thành viên; hãy chuyển thành viên trước khi đổi đơn vị");
        g.setOrganizationUnit(unit);
        g.setName(r.name().trim());
        g.setMandatoryCourses(mandatory);
        return dto(groups.save(g));
    }

    public void deleteGroup(String id) {
        auth.permission("GROUP_MANAGE");
        UserGroup g = groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group"));
        auth.scope(g.getOrganizationUnit());
        if (users.existsByUserGroupsId(id))
            throw new BadRequestException("Nhóm đang có thành viên");
        groups.delete(g);
    }

    private UserGroup memberGroup(String id, String action) {
        auth.requireOperation("groups", action);
        UserGroup group = groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group"));
        AccessPolicy.require(AccessPolicy.allows(auth.current(), "groups:" + action, group.getOrganizationUnit()));
        return group;
    }

    private boolean eligibleUnit(UserGroup group, OrganizationUnit unit) {
        return unit != null && (group.getOrganizationUnit() == null
                || AccessPolicy.ancestors(unit).contains(group.getOrganizationUnit().getId()));
    }

    @Transactional(readOnly = true)
    public GroupMembersPage members(String id, String query, int page, int size, boolean membersOnly) {
        UserGroup group = memberGroup(id, "read");
        if (page < 1 || size < 1 || size > 100)
            throw new BadRequestException("Phân trang không hợp lệ");
        User actor = auth.current();
        var scopedUnits = units.findAll().stream()
                .filter(unit -> AccessPolicy.allows(actor, "groups:read", unit)).toList();
        var scopeUnitIds = scopedUnits.stream().map(OrganizationUnit::getId).toList();
        var unitIds = scopedUnits.stream().filter(unit -> eligibleUnit(group, unit))
                .map(OrganizationUnit::getId).toList();
        boolean includeUnassigned = AccessPolicy.system(actor);
        long memberCount = users.countByUserGroupsId(id);
        if (scopeUnitIds.isEmpty() && !includeUnassigned)
            return new GroupMembersPage(List.of(), 0, 1, 1, memberCount);
        String search = "%" + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        var request = org.springframework.data.domain.PageRequest.of(page - 1, size,
                org.springframework.data.domain.Sort.by("fullName", "id"));
        var result = users.findGroupCandidates(unitIds, scopeUnitIds, includeUnassigned, id, membersOnly, search, request);
        int pageCount = Math.max(1, result.getTotalPages());
        if (page > pageCount) {
            page = pageCount;
            result = users.findGroupCandidates(unitIds, scopeUnitIds, includeUnassigned, id, membersOnly,
                    search, request.withPage(page - 1));
        }
        var items = result.getContent().stream().map(u -> new GroupMemberDto(u.getId(), u.getUsername(),
                u.getFullName(), u.getEmployeeId(), u.getOrganizationUnit() == null
                        ? "Chưa có đơn vị" : u.getOrganizationUnit().getName(), u.getStatus(),
                u.getUserGroups().stream().anyMatch(g -> g.getId().equals(id)),
                (!AccessPolicy.system(u) || u.getUserGroups().stream().anyMatch(g -> g.getId().equals(id)))
                        && AccessPolicy.allows(actor, "groups:update", group.getOrganizationUnit())
                        && AccessPolicy.allows(actor, "groups:update", u.getOrganizationUnit())))
                .toList();
        return new GroupMembersPage(items, result.getTotalElements(), page, pageCount, memberCount);
    }

    public void updateMembers(String id, MembershipChanges changes) {
        UserGroup group = memberGroup(id, "update");
        Set<Long> add = changes.addUserIds() == null ? Set.of() : changes.addUserIds();
        Set<Long> remove = changes.removeUserIds() == null ? Set.of() : changes.removeUserIds();
        if (!Collections.disjoint(add, remove))
            throw new BadRequestException("Không thể vừa thêm vừa bỏ cùng một thành viên");
        Set<Long> ids = new HashSet<>(add);
        ids.addAll(remove);
        if (ids.contains(null)) throw new BadRequestException("Học viên không hợp lệ");
        var targets = users.findAllById(ids);
        if (targets.size() != ids.size()) throw new BadRequestException("Học viên không tồn tại");
        User actor = auth.current();
        for (User target : targets) {
            AccessPolicy.require(AccessPolicy.allows(actor, "groups:update", target.getOrganizationUnit()));
            if (add.contains(target.getId())) {
                AccessPolicy.require(!AccessPolicy.system(target));
                if (!eligibleUnit(group, target.getOrganizationUnit()))
                    throw new BadRequestException("Học viên phải thuộc đơn vị của nhóm hoặc đơn vị con");
            }
        }
        for (User target : targets) {
            if (add.contains(target.getId())) {
                if (target.getUserGroups().stream().noneMatch(g -> g.getId().equals(id)))
                    target.getUserGroups().add(group);
            } else {
                target.getUserGroups().removeIf(g -> g.getId().equals(id));
            }
        }
        users.saveAll(targets);
        cache.invalidateAfterCommit();
    }

    public CourseAccessDto courseAccess(String id) {
        Course c = courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course"));
        auth.manage(c);
        return new CourseAccessDto(c.getOwnerUnit() == null ? null : c.getOwnerUnit().getId(),
                Map.copyOf(c.getUnitGrants()), Set.copyOf(c.getInstructorIds()));
    }

    public CourseAccessDto saveCourseAccess(String id, CourseAccessDto r) {
        Course c = courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course"));
        auth.manage(c);
        OrganizationUnit owner = r.ownerUnitId() == null || r.ownerUnitId().isBlank() ? null
                : auth.unit(r.ownerUnitId());
        auth.scope(owner);
        Map<String, String> grants = r.unitGrants() == null ? Map.of() : r.unitGrants();
        for (var grant : grants.entrySet()) {
            OrganizationUnit unit = auth.unit(grant.getKey());
            if (!AccessPolicy.inScope(auth.current(), unit))
                AccessPolicy.require(Objects.equals(c.getUnitGrants().get(grant.getKey()), grant.getValue()));
            if (!unit.isActive() || !Set.of("OPTIONAL", "MANDATORY").contains(grant.getValue()))
                throw new BadRequestException("Quyền chia sẻ khóa học không hợp lệ");
        }
        for (var old : c.getUnitGrants().entrySet())
            if (!AccessPolicy.inScope(auth.current(), auth.unit(old.getKey())))
                AccessPolicy.require(Objects.equals(grants.get(old.getKey()), old.getValue()));
        Set<Long> instructors = r.instructorIds() == null ? Set.of() : r.instructorIds();
        for (Long userId : instructors) {
            User instructor = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User"));
            if (!AccessPolicy.inScope(auth.current(), instructor.getOrganizationUnit()))
                AccessPolicy.require(c.getInstructorIds().contains(userId));
            if (!AccessPolicy.has(instructor, "courses:update") || !instructor.activeAccount())
                throw new BadRequestException("Người được phân công phải là giảng viên đang hoạt động");
        }
        for (Long old : c.getInstructorIds())
            if (!AccessPolicy.inScope(auth.current(), users.findById(old).orElseThrow().getOrganizationUnit()))
                AccessPolicy.require(instructors.contains(old));
        c.setOwnerUnit(owner);
        c.setUnitGrants(new HashMap<>(grants));
        c.setInstructorIds(new HashSet<>(instructors));
        courses.save(c);
        return courseAccess(id);
    }
}
