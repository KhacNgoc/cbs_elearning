package com.bidv.coretraining.quiz.service;

import com.bidv.coretraining.quiz.dto.QuizContextDto.*;
import java.util.*;

/** Business operations for QuizContextService. */
public interface QuizContextService {
    public List<Context> courses();

    public Context context(String courseId, String lessonId);
}
