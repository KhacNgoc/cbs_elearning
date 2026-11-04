package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AuthorizationServiceImpl implements AuthorizationService {
    private final UserRepository users;
    private final CourseRepository courses;
    private final OrganizationUnitRepository units;

    public AuthorizationServiceImpl(UserRepository users, CourseRepository courses, OrganizationUnitRepository units) {
        this.users = users;
        this.courses = courses;
        this.units = units;
    }

    /**
     * Resolve the authenticated identity in the caller's persistence context.
     * Callers reading entity relationships must keep their own transaction open
     * through authorization and DTO mapping. The security principal is detached.
     */
    public User current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        AccessPolicy.require(auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()));
        User user = (auth.getPrincipal() instanceof CustomUserDetails details
                ? users.findById(details.getUser().getId())
                : users.findByUsername(auth.getName()))
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        AccessPolicy.require(user.activeAccount()
                && (AccessPolicy.system(user) || AccessPolicy.activeUnit(user.getOrganizationUnit())));
        return user;
    }

    public void requireOperation(String page, String action) {
        var op = AdminOperation.current(page, action);
        AccessPolicy.require(AccessPolicy.has(current(), op.permission()));
    }

    public void permission(String permission) {
        AccessPolicy.require(AccessPolicy.system(current()) || AccessPolicy.permissions(current()).contains(permission));
        var op = AdminOperation.current("unknown", "denied");
        if (!op.page().equals("unknown"))
            AccessPolicy.require(AccessPolicy.has(current(), op.permission()));
    }

    public boolean allowedScope(OrganizationUnit unit) {
        var op = AdminOperation.current("users", "read");
        return AccessPolicy.allows(current(), op.permission(), unit);
    }

    public void program(TrainingProgram p, String page, String action) {
        AccessPolicy.require(AccessPolicy.allowsProgram(current(), page + ":" + action, p));
    }

    public void edit(Course course) {
        var op = AdminOperation.current("courses", "update");
        AccessPolicy.require(AccessPolicy.allowsCourse(current(), op.permission(), course));
    }

    public Course edit(String id) {
        Course c = courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course"));
        edit(c);
        return c;
    }

    public void manage(Course c) {
        edit(c);
        User u = current();
        var op = AdminOperation.current("courses", "update");
        if (u.isDynamicAccessInitialized() && !AccessPolicy.system(u))
            AccessPolicy.require(AccessPolicy.policies(u).stream().anyMatch(
                    r -> !r.assignedCoursesOnly() && AccessPolicy.allowsCourse(withPolicy(u, r), op.permission(), c)));
    }

    private User withPolicy(User u, RolePolicy r) {
        return User.builder().id(u.getId()).status(u.getStatus()).organizationUnit(u.getOrganizationUnit())
                .dynamicAccessInitialized(true).policySnapshot(List.of(r)).build();
    }

    public void user(User target) {
        permission("USER_MANAGE");
        AccessPolicy.require(AccessPolicy.system(current())
                || !AccessPolicy.system(target) && allowedScope(target.getOrganizationUnit()));
    }

    public OrganizationUnit unit(String id) {
        return units.findById(id).orElseThrow(() -> new ResourceNotFoundException("Unit"));
    }

    public void scope(OrganizationUnit unit) {
        AccessPolicy.require(allowedScope(unit));
    }

    public Set<String> scopeIds() {
        User u = current();
        return units.findAll().stream().filter(this::allowedScope).map(OrganizationUnit::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public List<Course> editableCourses() {
        User u = current();
        var op = AdminOperation.current("courses", "read");
        return courses.findAll().stream().filter(c -> AccessPolicy.allowsCourse(u, op.permission(), c)).toList();
    }

    @Override
    public List<Course> searchEditableCourses(String search, int limit) {
        User u = current();
        var op = AdminOperation.current("courses", "read");
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        int maxResults = (limit > 0 && limit <= 200) ? limit : 50;
        return courses.searchActiveCourses(query, org.springframework.data.domain.PageRequest.of(0, maxResults))
                .getContent()
                .stream()
                .filter(c -> (c.getCode() == null || !c.getCode().contains("_DEL_"))
                        && AccessPolicy.allowsCourse(u, op.permission(), c))
                .toList();
    }

    public List<User> manageableUsers() {
        User u = current();
        permission("USER_MANAGE");
        return AccessPolicy.system(u) ? users.findAll()
                : users.findByOrganizationUnitIdIn(scopeIds()).stream().filter(t -> !AccessPolicy.system(t)).toList();
    }
}
