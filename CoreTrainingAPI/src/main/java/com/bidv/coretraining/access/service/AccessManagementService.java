package com.bidv.coretraining.access.service;

import com.bidv.coretraining.access.dto.AccessManagementDto.*;
import java.util.*;

/** Business operations for AccessManagementService. */
public interface AccessManagementService {
    public List<UnitDto> units();

    public UnitDto saveUnit(String id, UnitDto r);

    public CourseAccessDto courseAccess(String id);

    public CourseAccessDto saveCourseAccess(String id, CourseAccessDto r);
}
