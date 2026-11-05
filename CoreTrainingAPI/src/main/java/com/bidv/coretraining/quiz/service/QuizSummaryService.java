package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.quiz.dto.QuizSummaryPageDto.*;
import java.util.*;

/** Business operations for QuizSummaryService. */
public interface QuizSummaryService {
    public Result list(String courseId, String search, int page, int size);
}
