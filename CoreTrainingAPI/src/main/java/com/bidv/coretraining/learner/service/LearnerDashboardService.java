package com.bidv.coretraining.learner.service;

import com.bidv.coretraining.learner.dto.LearnerDashboardDto.*;
import com.bidv.coretraining.learner.dto.LearnerWorkspaceDto.Resume;
import java.util.*;
public interface LearnerDashboardService {
 Overview overview(String tab);
 Resume resume(String id);
 Page page(Filter filter);
 List<CourseChoice> choices(String search,int size);
}
