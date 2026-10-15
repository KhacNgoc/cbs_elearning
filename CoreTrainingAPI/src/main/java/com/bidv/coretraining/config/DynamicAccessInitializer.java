package com.bidv.coretraining.config;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
@Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class DynamicAccessInitializer {
    private final AccessRoleRepository roles;
    private final AdminPageRepository pages;
    private final NavigationItemRepository navigation;
    private final UserRepository users;
    private final CourseRepository courses;
    private final TrainingProgramRepository programs;

    public DynamicAccessInitializer(AccessRoleRepository roles, AdminPageRepository pages,
            NavigationItemRepository navigation, UserRepository users, CourseRepository courses,
            TrainingProgramRepository programs) {
        this.roles = roles;
        this.pages = pages;
        this.navigation = navigation;
        this.users = users;
        this.courses = courses;
        this.programs = programs;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void run() {
        page("overview", "Tổng quan", "/admin", 10, "read");
        page("catalog", "Danh mục đào tạo", "/admin/catalog", 20, "create", "read", "update");
        page("courses", "Khóa học và nội dung", "/admin/courses", 30, "create", "read", "update", "delete");
        page("users", "Tài khoản", "/admin/users", 40, "create", "read", "update", "delete");
        page("units", "Đơn vị / phòng", "/admin/units", 50, "create", "read", "update");
        page("groups", "Nhóm học viên", "/admin/groups", 60, "create", "read", "update", "delete");
        page("roles", "Quản lý role", "/admin/roles", 70, "create", "read", "update", "delete");
        page("permissions", "Phân quyền", "/admin/permissions", 80, "read", "update");
        page("enrollments", "Duyệt đăng ký", "/admin?tab=enrollments", 90, "read", "update");
        page("exams", "Đề thi trắc nghiệm", "/admin?tab=exams", 100, "create", "read", "update", "delete");
        page("documents", "Tài liệu AI", "/admin?tab=documents", 110, "create", "read", "delete");
        Set<String> all = new HashSet<>();
        pages.findAll().forEach(p -> p.getActions().forEach(a -> all.add(p.getId() + ":" + a)));
        for (AccessRole r : roles.findAll())
            if (r.isProtectedRole()) {
                r.setPermissions(new HashSet<>(all));
                roles.save(r);
            }
        for (NavigationItem n : navigation.findAll())
            if (n.getId().startsWith("admin.")) {
                String key = n.getId().substring(6);
                if (key.startsWith("quiz"))
                    key = "exams";
                if (key.startsWith("lesson"))
                    key = "courses";
                if (key.equals("access"))
                    key = "permissions";
                n.setPermissionPage(key);
                navigation.save(n);
            }
        if (!navigation.existsById("admin.roles"))
            navigation.save(NavigationItem.builder().id("admin.roles").label("Quản lý role").route("/admin/roles")
                    .icon("UsersRound").area("ADMIN").sortOrder(113).visible(true).permissionPage("roles").build());
        // Only derive programme ownership on first migration; never overwrite later
        // manual choices.
        for (TrainingProgram p : programs.findAll())
            if (p.getUnits().isEmpty()) {
                courses.findAll().stream().filter(c -> c.getProgram() != null
                        && c.getProgram().getId().equals(p.getId()) && c.getOwnerUnit() != null)
                        .forEach(c -> p.getUnits().add(c.getOwnerUnit()));
                programs.save(p);
            }
        for (User u : users.findAll())
            if (!u.isDynamicAccessInitialized()) {
                Set<AccessRole> memberships = new HashSet<>();
                for (String code : u.effectiveRoles()) {
                    Set<String> scope = u.getScopeUnitIds().isEmpty()
                            ? (u.getOrganizationUnit() == null ? Set.of() : Set.of(u.getOrganizationUnit().getId()))
                            : Set.copyOf(u.getScopeUnitIds());
                    String key = code
                            + (Set.of("ROLE_UNIT_ADMIN", "ROLE_REPORT_VIEWER").contains(code)
                                    ? "_" + UUID.nameUUIDFromBytes(
                                            new TreeSet<>(scope).stream().map(value -> value.length() + ":" + value)
                                                    .collect(java.util.stream.Collectors.joining())
                                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                            .toString()
                                    : "");
                    AccessRole role = roles.findByCode(key).orElse(null);
                    if (role == null) {
                        Set<String> permissions = new HashSet<>();
                        String name;
                        switch (code) {
                            case "ROLE_ADMIN" -> {
                                name = "Quản trị viên hệ thống";
                                permissions.addAll(all);
                            }
                            case "ROLE_UNIT_ADMIN" -> {
                                name = "Quản trị viên đơn vị";
                                for (String page : List.of("courses", "users", "groups", "roles", "exams", "documents"))
                                    pages.findById(page).ifPresent(
                                            p -> p.getActions().forEach(a -> permissions.add(page + ":" + a)));
                                permissions.addAll(Set.of("permissions:read", "permissions:update", "units:read",
                                        "enrollments:read", "enrollments:update", "overview:read"));
                            }
                            case "ROLE_INSTRUCTOR" -> {
                                name = "Giảng viên / quản lý nội dung";
                                for (String page : List.of("courses", "exams", "documents"))
                                    pages.findById(page).ifPresent(
                                            p -> p.getActions().forEach(a -> permissions.add(page + ":" + a)));
                            }
                            case "ROLE_REPORT_VIEWER" -> {
                                name = "Lãnh đạo / người xem báo cáo";
                                permissions.add("overview:read");
                            }
                            default -> name = "Học viên";
                        }
                        role = roles.save(AccessRole.builder().code(key)
                                .name(name + (key.equals(code) ? ""
                                        : " · " + (scope.isEmpty() ? "chưa gán đơn vị"
                                                : String.join(", ", new TreeSet<>(scope)))))
                                .protectedRole(code.equals("ROLE_ADMIN"))
                                .learningEnabled(Set.of("ROLE_ADMIN", "ROLE_USER").contains(code))
                                .assignedCoursesOnly(code.equals("ROLE_INSTRUCTOR"))
                                .legacyOwnerScope(code.equals("ROLE_UNIT_ADMIN")).permissions(permissions)
                                .unitIds(new HashSet<>(scope)).build());
                    }
                    memberships.add(role);
                }
                u.setAssignedRoles(memberships);
                u.setDynamicAccessInitialized(true);
                users.save(u);
            }
    }

    private void page(String id, String name, String route, int order, String... actions) {
        if (!pages.existsById(id))
            pages.save(AdminPage.builder().id(id).name(name).route(route).sortOrder(order)
                    .actions(new HashSet<>(List.of(actions))).build());
    }
}
