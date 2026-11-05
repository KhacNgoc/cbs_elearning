package com.bidv.coretraining.access.service;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.user.entity.OrganizationUnit;
import com.bidv.coretraining.user.entity.User;
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
