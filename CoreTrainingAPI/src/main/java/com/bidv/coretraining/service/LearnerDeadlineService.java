package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LearnerDeadlineDto.*;
import java.util.List;

public interface LearnerDeadlineService {
    List<Deadline> list(Long userId);
    void save(Long userId, Request request);
}
