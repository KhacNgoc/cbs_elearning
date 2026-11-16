package com.bidv.coretraining.learner.service;

import com.bidv.coretraining.learner.dto.LearnerDeadlineDto.*;
import com.bidv.coretraining.training.dto.PagedResult;
import java.util.List;

public interface LearnerDeadlineService {
    List<Deadline> list(Long userId);
    PagedResult<Deadline> page(Long userId, String search, int page, int size);
    void save(Long userId, Request request);
}
