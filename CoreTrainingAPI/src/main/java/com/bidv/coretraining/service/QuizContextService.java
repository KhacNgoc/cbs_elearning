package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizContextDto.*;
import java.util.*;

/** Business operations for QuizContextService. */
public interface QuizContextService {
    public List<Context> courses();

    public Context context(String courseId, String lessonId);
}
