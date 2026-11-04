package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LearnerDashboardDto.*;
import com.bidv.coretraining.dto.LearnerWorkspaceDto.Resume;
import java.util.*;
public interface LearnerDashboardService {
 Overview overview(String tab);
 Resume resume(String id);
 Page page(Filter filter);
 List<CourseChoice> choices(String search,int size);
}
