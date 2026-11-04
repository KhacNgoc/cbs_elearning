package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizSummaryPageDto.*;
import java.util.*;

/** Business operations for QuizSummaryService. */
public interface QuizSummaryService {
    public Result list(String courseId, String search, int page, int size);
}
