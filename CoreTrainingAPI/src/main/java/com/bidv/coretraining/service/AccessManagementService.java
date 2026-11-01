package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.AccessManagementDto.*;
import java.util.*;

/** Business operations for AccessManagementService. */
public interface AccessManagementService {
    public List<UnitDto> units();

    public UnitDto saveUnit(String id, UnitDto r);

    public List<GroupDto> groups();

    public GroupDto saveGroup(String id, GroupDto r);

    public void deleteGroup(String id);

    public GroupMembersPage members(String id, String query, int page, int size, boolean membersOnly);

    public void updateMembers(String id, MembershipChanges changes);

    public CourseAccessDto courseAccess(String id);

    public CourseAccessDto saveCourseAccess(String id, CourseAccessDto r);
}
