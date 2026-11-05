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
    private final UserRepository users;
    private final CourseRepository courses;

    public AccessManagementServiceImpl(AuthorizationService auth, OrganizationUnitRepository units,
            UserRepository users, CourseRepository courses,
            PermissionCache cache) {
        this.auth = auth;
        this.units = units;
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
