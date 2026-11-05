package com.bidv.coretraining.security;

import com.bidv.coretraining.entity.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

/**
 * One source of truth for role permissions and object scopes. Legacy ROLE_ADMIN
 * is global.
 */
public final class AccessPolicy {
    private AccessPolicy() {
    }

    public static List<RolePolicy> policies(User u) {
        return u.getPolicySnapshot() != null ? u.getPolicySnapshot()
                : u.getAssignedRoles().stream().map(RolePolicy::of).toList();
    }

    public static Map<String, String> roleNames(User u) {
        Map<String, String> names = new HashMap<>();
        policies(u).forEach(r -> names.put(r.code(), r.name()));
        return names;
    }

    public static boolean has(User u, String permission) {
        if (system(u))
            return true;
        Set<String> perms = permissions(u);
        if (perms.contains(permission))
            return true;
        if (permission != null && permission.startsWith("catalog:"))
            return perms.contains("courses:" + permission.substring("catalog:".length()));
        return false;
    }

    public static boolean roleScope(RolePolicy r, OrganizationUnit unit) {
        return !r.learnerRole() && unit != null && activeUnit(unit)
                && ancestors(unit).stream().anyMatch(r.unitIds()::contains);
    }

    public static boolean allows(User u, String permission, OrganizationUnit unit) {
        if (system(u))
            return true;
        if (!u.isDynamicAccessInitialized())
            return inScope(u, unit);
        return policies(u).stream()
                .anyMatch(r -> r.active() && (r.permissions().contains(permission)
                        || (permission != null && permission.startsWith("catalog:")
                                && r.permissions().contains("courses:" + permission.substring("catalog:".length()))))
                        && roleScope(r, unit));
    }

    public static boolean allowsProgram(User u, String permission, TrainingProgram p) {
        if (system(u))
            return true;
        if (p == null)
            return false;
        return p.getUnits().stream().anyMatch(n -> allows(u, permission, n));
    }

    public static boolean allowsCourse(User u, String permission, Course c) {
        if (system(u))
            return true;
        if (!u.isDynamicAccessInitialized())
            return canEdit(u, c);
        return policies(u).stream().anyMatch(r -> r.active() && !r.learnerRole() && r.permissions().contains(permission) &&
                (r.assignedCoursesOnly() ? c.getInstructorIds().contains(u.getId())
                        : r.legacyOwnerScope() ? roleScope(r, c.getOwnerUnit())
                                : c.getProgram() != null
                                        && c.getProgram().getUnits().stream().anyMatch(n -> roleScope(r, n))));
    }

    public static Set<String> courseActions(User u, Course c) {
        return java.util.stream.Stream.of("read", "create", "update", "delete")
                .filter(a -> allowsCourse(u, "courses:" + a, c)).collect(java.util.stream.Collectors.toSet());
    }

    public static boolean canReadCourse(User u, Course c) {
        return u.activeAccount() && (system(u) || activeUnit(u.getOrganizationUnit()))
                && allowsCourse(u, "courses:read", c);
    }

    public static Set<String> permissions(User u) {
        if (u.isDynamicAccessInitialized()) {
            Set<String> result = new HashSet<>();
            for (RolePolicy r : policies(u))
                if (r.active()) {
                    if (!r.learnerRole()) result.addAll(r.permissions());
                    if (r.learnerRole() || r.learningEnabled())
                        result.add("LEARN");
                }
            // Compatibility aliases for existing non-route gates; route and resource checks
            // remain action-specific.
            Map<String, String> aliases = Map.of("users", "USER_MANAGE", "units",
                    "ORG_MANAGE", "catalog", "CATALOG_MANAGE", "courses", "COURSE_MANAGE", "enrollments",
                    "ENROLLMENT_MANAGE", "permissions", "ROLE_ASSIGN");
            aliases.forEach((page, alias) -> {
                if (result.stream().anyMatch(p -> p.startsWith(page + ":")))
                    result.add(alias);
            });
            if (result.stream()
                    .anyMatch(p -> p.startsWith("courses:") || p.startsWith("exams:") || p.startsWith("documents:")))
                result.add("CONTENT_EDIT");
            if (result.stream().anyMatch(p -> p.startsWith("courses:") || p.startsWith("catalog:")))
                result.addAll(Set.of("CATALOG_MANAGE", "COURSE_MANAGE"));
            if (system(u))
                result.addAll(Set.of("LEARN", "CONTENT_EDIT", "DISCUSSION_REPLY", "USER_MANAGE",
                        "COURSE_MANAGE", "ENROLLMENT_MANAGE", "ROLE_ASSIGN", "REPORT_VIEW", "ORG_MANAGE",
                        "CATALOG_MANAGE"));
            return result;
        }
        Set<String> p = new HashSet<>();
        if (u.hasRole("ROLE_USER"))
            p.add("LEARN");
        if (u.hasRole("ROLE_INSTRUCTOR"))
            p.addAll(Set.of("CONTENT_EDIT", "DISCUSSION_REPLY"));
        if (u.hasRole("ROLE_UNIT_ADMIN"))
            p.addAll(Set.of("USER_MANAGE", "COURSE_MANAGE", "CONTENT_EDIT", "ENROLLMENT_MANAGE",
                    "ROLE_ASSIGN", "REPORT_VIEW"));
        if (u.hasRole("ROLE_REPORT_VIEWER"))
            p.add("REPORT_VIEW");
        if (system(u))
            p.addAll(Set.of("LEARN", "CONTENT_EDIT", "DISCUSSION_REPLY", "USER_MANAGE", "COURSE_MANAGE",
                    "ENROLLMENT_MANAGE", "ROLE_ASSIGN", "REPORT_VIEW", "ORG_MANAGE", "CATALOG_MANAGE"));
        Map<String, String> aliases = Map.of("users", "USER_MANAGE", "units", "ORG_MANAGE",
                "catalog", "CATALOG_MANAGE", "courses", "CONTENT_EDIT", "enrollments", "ENROLLMENT_MANAGE",
                "permissions", "ROLE_ASSIGN", "roles", "ROLE_ASSIGN", "exams", "CONTENT_EDIT");
        aliases.forEach((page, old) -> {
            if (p.contains(old))
                for (String action : List.of("create", "read", "update", "delete"))
                    p.add(page + ":" + action);
        });
        if (p.contains("CONTENT_EDIT"))
            for (String a : List.of("create", "read", "delete"))
                p.add("documents:" + a);
        if (p.contains("USER_MANAGE") || p.contains("REPORT_VIEW"))
            p.add("overview:read");
        if (u.hasRole("ROLE_UNIT_ADMIN"))
            p.add("units:read");
        return p;
    }

    public static boolean system(User u) {
        return u.isDynamicAccessInitialized() ? policies(u).stream().anyMatch(r -> r.active() && r.protectedRole())
                : u.hasRole("ROLE_ADMIN");
    }

    public static Set<String> ancestors(OrganizationUnit unit) {
        Set<String> result = new HashSet<>();
        for (OrganizationUnit n = unit; n != null; n = n.getParent()) {
            if (!result.add(n.getId()))
                break;
        }
        return result;
    }

    public static boolean activeUnit(OrganizationUnit unit) {
        Set<String> seen = new HashSet<>();
        for (OrganizationUnit n = unit; n != null; n = n.getParent()) {
            if (!seen.add(n.getId()) || !n.isActive())
                return false;
        }
        return true;
    }

    public static boolean inScope(User u, OrganizationUnit unit) {
        if (system(u))
            return true;
        if (u.isDynamicAccessInitialized())
            return policies(u).stream().anyMatch(r -> r.active() && roleScope(r, unit));
        if (unit == null || !activeUnit(unit))
            return false;
        Set<String> scopes = u.getScopeUnitIds().isEmpty()
                ? (u.getOrganizationUnit() == null ? Set.of() : Set.of(u.getOrganizationUnit().getId()))
                : u.getScopeUnitIds();
        return ancestors(unit).stream().anyMatch(scopes::contains);
    }

    public static boolean canEdit(User u, Course c) {
        if (u.isDynamicAccessInitialized())
            return u.activeAccount() && (system(u) || activeUnit(u.getOrganizationUnit()))
                    && allowsCourse(u, "courses:update", c);
        return u.activeAccount() && (system(u) || activeUnit(u.getOrganizationUnit()) && activeUnit(c.getOwnerUnit()))
                && (system(u)
                        || u.hasRole("ROLE_UNIT_ADMIN") && inScope(u, c.getOwnerUnit())
                        || u.hasRole("ROLE_INSTRUCTOR") && c.getInstructorIds().contains(u.getId()));
    }

    public static boolean visible(User u, Course c) {
        if (canReadCourse(u, c) || canEdit(u, c))
            return true;
        if (u.getOrganizationUnit() == null || !activeUnit(u.getOrganizationUnit()))
            return c.getOwnerUnit() == null && u.getOrganizationUnit() == null;

        Set<String> units = ancestors(u.getOrganizationUnit());
        if (u.getRegisteredCourses() != null
                && u.getRegisteredCourses().stream().anyMatch(rc -> rc.getId().equals(c.getId())))
            return true;
        if (c.getUnitGrants() != null && c.getUnitGrants().keySet().stream().anyMatch(units::contains))
            return true;
        if (c.getProgram() != null) {
            if (c.getProgram().getUnitGrants() != null
                    && c.getProgram().getUnitGrants().keySet().stream().anyMatch(units::contains))
                return true;
            if (c.getProgram().getUnits() != null && !c.getProgram().getUnits().isEmpty()
                    && c.getProgram().getUnits().stream()
                            .anyMatch(n -> activeUnit(n) && units.contains(n.getId())))
                return true;
            if (c.getProgram().getDomain() != null) {
                if (c.getProgram().getDomain().getUnitGrants() != null
                        && c.getProgram().getDomain().getUnitGrants().keySet().stream().anyMatch(units::contains))
                    return true;
                if (c.getProgram().getDomain().getUnits() != null
                        && c.getProgram().getDomain().getUnits().stream()
                                .anyMatch(n -> activeUnit(n) && units.contains(n.getId())))
                    return true;
            }
        }
        if (c.getOwnerUnit() != null && activeUnit(c.getOwnerUnit()) && units.contains(c.getOwnerUnit().getId()))
            return true;
        return false;
    }

    public static boolean mandatory(User u, Course c) {
        if (u.getOrganizationUnit() == null || !activeUnit(u.getOrganizationUnit()))
            return false;
        Set<String> units = ancestors(u.getOrganizationUnit());
        // 1. Ưu tiên cấu hình trực tiếp trên Khóa học (Course-level grant)
        if (c.getUnitGrants() != null && !c.getUnitGrants().isEmpty()) {
            boolean hasCourseGrant = c.getUnitGrants().keySet().stream().anyMatch(units::contains);
            if (hasCourseGrant) {
                return c.getUnitGrants().entrySet().stream()
                        .anyMatch(e -> units.contains(e.getKey()) && "MANDATORY".equalsIgnoreCase(e.getValue()));
            }
        }
        // 2. Kế thừa từ Chương trình đào tạo (Program-level grant)
        if (c.getProgram() != null && c.getProgram().getUnitGrants() != null
                && !c.getProgram().getUnitGrants().isEmpty()) {
            boolean hasProgramGrant = c.getProgram().getUnitGrants().keySet().stream().anyMatch(units::contains);
            if (hasProgramGrant) {
                return c.getProgram().getUnitGrants().entrySet().stream()
                        .anyMatch(e -> units.contains(e.getKey()) && "MANDATORY".equalsIgnoreCase(e.getValue()));
            }
        }
        // 3. Kế thừa từ Lĩnh vực đào tạo (Domain-level grant)
        if (c.getProgram() != null && c.getProgram().getDomain() != null
                && c.getProgram().getDomain().getUnitGrants() != null) {
            return c.getProgram().getDomain().getUnitGrants().entrySet().stream()
                    .anyMatch(e -> units.contains(e.getKey()) && "MANDATORY".equalsIgnoreCase(e.getValue()));
        }
        return false;
    }

    public static void require(boolean allowed) {
        if (!allowed)
            throw new AccessDeniedException("Bạn không có quyền truy cập chức năng hoặc dữ liệu này");
    }
}
