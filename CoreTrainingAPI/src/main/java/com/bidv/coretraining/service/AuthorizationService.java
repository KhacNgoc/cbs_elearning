package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import java.util.*;

/** Business operations for AuthorizationService. */
public interface AuthorizationService {
    public User current();

    public void requireOperation(String page, String action);

    public void permission(String permission);

    public boolean allowedScope(OrganizationUnit unit);

    public void program(TrainingProgram p, String page, String action);

    public void edit(Course course);

    public Course edit(String id);

    public void manage(Course c);

    public void user(User target);

    public OrganizationUnit unit(String id);

    public void scope(OrganizationUnit unit);

    public Set<String> scopeIds();

    public List<Course> editableCourses();

    public List<Course> searchEditableCourses(String search, int limit);

    public List<User> manageableUsers();
}
