package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.quiz.dto.LearnerQuizEntryDto.*;
import java.util.*;

/** Business operations for LearnerQuizEntryService. */
public interface LearnerQuizEntryService {
    public Context context(String courseId,String lessonId);
}
