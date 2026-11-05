package com.bidv.coretraining.learner.service;

import com.bidv.coretraining.learner.dto.LearnerDeadlineDto.*;
import java.util.List;

public interface LearnerDeadlineService {
    List<Deadline> list(Long userId);
    void save(Long userId, Request request);
}
