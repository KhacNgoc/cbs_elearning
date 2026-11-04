package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.dto.AccessManagementDto;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.RoleManagementDto.*;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.*;
import com.bidv.coretraining.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleManagementServiceImpl implements RoleManagementService {

    private final AccessRoleRepository roles;
    private final UserRepository users;
    private final OrganizationUnitRepository units;
    private final AdminPageRepository pages;
    private final AuthorizationService auth;
    private final PermissionCache cache;

    public RoleManagementServiceImpl(AccessRoleRepository roles, UserRepository users, OrganizationUnitRepository units,
            AdminPageRepository pages, AuthorizationService auth, PermissionCache cache) {
        this.roles = roles;
        this.users = users;
        this.units = units;
        this.pages = pages;
        this.auth = auth;
        this.cache = cache;
    }

    private AccessRole role(String id) {
        return roles.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Role"));
    }

    private void checkVersion(AccessRole r, Long v) {
        if (v != null && !Objects.equals(v, r.getVersion()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Role đã thay đổi. Hãy tải lại trước khi lưu");
    }

    private boolean delegable(AccessRole r) {
        User a = auth.current();
        if (AccessPolicy.system(a))
            return true;
        if (r.isProtectedRole() || r.isAssignedCoursesOnly())
            return false;
        if ((r.isLearnerRole() || r.isLearningEnabled()) && !AccessPolicy.has(a, "LEARN"))
            return false;
        var op = AdminOperation.current("roles", "read");
        return r.getPermissions().stream().allMatch(p -> AccessPolicy.has(a, p))
                && r.getUnitIds().stream().allMatch(id -> AccessPolicy.allows(a, op.permission(), auth.unit(id))
                        && r.getPermissions().stream().allMatch(p -> AccessPolicy.allows(a, p, auth.unit(id))));
    }

    private void mutate(AccessRole r) {
        AccessPolicy.require(!r.isProtectedRole() && delegable(r));
    }

    private RoleDto dto(AccessRole r) {
        return new RoleDto(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isActive(), r.isProtectedRole(),
                r.isLearningEnabled(), r.isLearnerRole(), r.isAssignedCoursesOnly(), r.getVersion(), Set.copyOf(r.getPermissions()),
                Set.copyOf(r.getUnitIds()), new HashSet<>(users.findIdsByAssignedRole(r.getId())));
    }

    public List<RoleDto> list() {
        auth.requireOperation("roles", "read");
        return roles.findAll().stream().filter(this::delegable)
                .sorted(Comparator.comparing(AccessRole::isProtectedRole).reversed().thenComparing(AccessRole::getName))
                .map(this::dto).toList();
    }

    public List<MemberDto> members() {
        auth.requireOperation("roles", "read");
        User a = auth.current();
        return users.findAll().stream()
                .filter(u -> AccessPolicy.system(a)
                        || !AccessPolicy.system(u) && AccessPolicy.allows(a, "roles:update", u.getOrganizationUnit()))
                .map(u -> new MemberDto(u.getId(), u.getUsername(), u.getFullName(),
                        u.getOrganizationUnit() == null ? null : u.getOrganizationUnit().getId(), u.effectiveRoles()))
                .toList();
    }

    private String generateRoleCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = "ROLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (roles.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "ROLE_" + System.currentTimeMillis();
    }

    public RoleDto save(String id, RoleRequest request) {
        auth.requireOperation("roles", id == null ? "create" : "update");
        AccessRole r = id == null ? AccessRole.builder().build() : role(id);
        if (id != null) {
            mutate(r);
            checkVersion(r, request.version());
        }
        String code;
        if (id == null) {
            code = (request.code() == null || request.code().isBlank())
                    ? generateRoleCode()
                    : request.code().trim().toUpperCase(Locale.ROOT);
        } else {
            code = (request.code() == null || request.code().isBlank())
                    ? r.getCode()
                    : request.code().trim().toUpperCase(Locale.ROOT);
        }
        if (!code.matches("[A-Za-z0-9_\\-]{2,80}"))
            throw new BadRequestException("Mã role gồm 2–80 chữ, số, dấu gạch");
        if (request.name() == null || request.name().isBlank())
            throw new BadRequestException("Tên role không được để trống");
        roles.findByCode(code).ifPresent(other -> {
            if (!other.getId().equals(r.getId()))
                throw new BadRequestException("Mã role đã tồn tại");
        });
        boolean learner = request.learnerRole() == null ? r.isLearnerRole() : request.learnerRole();
        AccessPolicy.require(!(learner || request.learningEnabled()) || AccessPolicy.has(auth.current(), "LEARN"));
        r.setCode(code);
        r.setName(request.name().trim());
        r.setDescription(request.description());
        r.setActive(request.active());
        r.setLearnerRole(learner);
        r.setLearningEnabled(learner || request.learningEnabled());
        if (learner) {
            r.setPermissions(new HashSet<>());
            r.setUnitIds(new HashSet<>());
            r.setAssignedCoursesOnly(false);
            r.setLegacyOwnerScope(false);
        }
        roles.saveAndFlush(r);
        cache.invalidateAfterCommit();
        return dto(r);
    }

    public RoleDto grants(String id, GrantRequest request) {
        auth.requireOperation("permissions", "update");
        AccessRole r = role(id);
        mutate(r);
        if (r.isLearnerRole())
            throw new BadRequestException("Role học viên không có phân quyền quản trị chi tiết. Nội dung học được xác định theo đơn vị của tài khoản.");
        checkVersion(r, request.version());
        Set<String> p = new HashSet<>(request.permissions() == null ? Set.of() : request.permissions()),
                scope = new HashSet<>(request.unitIds() == null ? Set.of() : request.unitIds());
        if (scope.isEmpty())
            throw new BadRequestException("Phạm vi quản trị dữ liệu bắt buộc chọn ít nhất 1 đơn vị");
        Set<String> valid = pages.findAll().stream()
                .flatMap(page -> page.getActions().stream().map(action -> page.getId() + ":" + action))
                .collect(Collectors.toSet());
        if (!valid.containsAll(p))
            throw new BadRequestException("Quyền không nằm trong danh mục chức năng quản trị");
        for (String value : List.copyOf(p))
            p.add(value.substring(0, value.indexOf(':')) + ":read");
        User a = auth.current();
        for (String value : p)
            AccessPolicy.require(AccessPolicy.has(a, value));
        for (String unitId : scope) {
            var n = auth.unit(unitId);
            AccessPolicy.require(AccessPolicy.allows(a, "permissions:update", n));
            if (!AccessPolicy.activeUnit(n))
                throw new BadRequestException("Đơn vị đã ngừng hoạt động");
            for (String value : p)
                AccessPolicy.require(AccessPolicy.allows(a, value, n));
        }
        r.setPermissions(p);
        r.setUnitIds(scope);
        r.setAssignedCoursesOnly(false);
        r.setLegacyOwnerScope(false);
        roles.saveAndFlush(r);
        cache.invalidateAfterCommit();
        return dto(r);
    }

    public RoleDto assign(String id, MembersRequest request) {
        auth.requireOperation("roles", "update");
        AccessRole r = role(id);
        if (r.isProtectedRole())
            AccessPolicy.require(AccessPolicy.system(auth.current()));
        else
            mutate(r);
        checkVersion(r, request.version());
        Set<Long> desired = request.userIds() == null ? Set.of() : request.userIds();
        List<User> all = users.findAll();
        if (!all.stream().map(User::getId).collect(Collectors.toSet()).containsAll(desired))
            throw new BadRequestException("Tài khoản không tồn tại");
        if (r.isProtectedRole() && all.stream().noneMatch(u -> desired.contains(u.getId()) && u.activeAccount()))
            throw new BadRequestException("Phải giữ ít nhất một quản trị viên hệ thống đang hoạt động");
        User a = auth.current();
        for (User u : all) {
            boolean existing = u.getAssignedRoles().stream().anyMatch(x -> x.getId().equals(id));
            boolean next = desired.contains(u.getId());
            if (existing == next)
                continue;
            AccessPolicy
                    .require(AccessPolicy.system(a) || !AccessPolicy.system(u) && !Objects.equals(a.getId(), u.getId())
                            && AccessPolicy.allows(a, "roles:update", u.getOrganizationUnit()));
            if (next)
                u.getAssignedRoles().add(r);
            else
                u.getAssignedRoles().removeIf(x -> x.getId().equals(id));
            u.setDynamicAccessInitialized(true);
            users.save(u);
        }
        // Membership edits participate in optimistic locking on the role.
        r.setMembershipRevision(r.getMembershipRevision() + 1);
        roles.saveAndFlush(r);
        cache.invalidateAfterCommit();
        return dto(r);
    }

    public void delete(String id) {
        auth.requireOperation("roles", "delete");
        AccessRole r = role(id);
        mutate(r);
        if (users.findAll().stream().anyMatch(u -> u.getAssignedRoles().stream().anyMatch(x -> x.getId().equals(id))))
            throw new BadRequestException("Role đang có tài khoản. Hãy gỡ thành viên trước khi xóa");
        roles.delete(r);
        cache.invalidateAfterCommit();
    }

    public List<PageDto> pages() {
        auth.requireOperation("permissions", "read");
        return pages.findAllByOrderBySortOrderAsc().stream()
                .map(p -> new PageDto(p.getId(), p.getName(), p.getRoute(), p.getSortOrder(), Set.copyOf(p.getActions())))
                .toList();
    }

    public List<AccessManagementDto.UnitDto> units() {
        var op = AdminOperation.current("permissions", "read");
        auth.requireOperation(op.page(), op.action());
        User a = auth.current();
        return units.findAll().stream().filter(n -> AccessPolicy.inScope(a, n))
                .map(n -> new AccessManagementDto.UnitDto(n.getId(), n.getName(), n.getType(),
                        n.getParent() == null ? null : n.getParent().getId(), n.isActive()))
                .toList();
    }
}
