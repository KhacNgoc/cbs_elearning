package com.bidv.coretraining.quiz.service.impl;
import com.bidv.coretraining.quiz.dto.QuizSummaryDto;
import com.bidv.coretraining.quiz.dto.QuizSummaryPageDto;


import com.bidv.coretraining.quiz.dto.QuizSummaryPageDto.*;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.quiz.repository.QuestionRepository;
import com.bidv.coretraining.quiz.service.QuizSummaryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class QuizSummaryServiceImpl implements QuizSummaryService {
    private final AuthorizationService authorization;
    private final QuestionRepository questions;
    public QuizSummaryServiceImpl(AuthorizationService authorization, QuestionRepository questions) {
        this.authorization = authorization;
        this.questions = questions;
    }

    public Result list(String courseId, String search, int page, int size) {
        authorization.requireOperation("exams", "read");
        String course = courseId == null || courseId.isBlank() ? null : courseId;
        // System admins read every course: skip loading all courses just to check each one.
        boolean allCourses = AccessPolicy.system(authorization.current());
        List<String> ids = allCourses ? List.of("") : authorization.editableCourses().stream()
                .map(Course::getId).toList();
        if (ids.isEmpty() || (!allCourses && course != null && !ids.contains(course)))
            return new Result(List.of(), 0, 0, List.of());
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        // Treat search text literally, including SQL LIKE wildcard characters.
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = questions.findQuizSummaryPage(allCourses, ids, course, pattern,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
        List<String> quizIds = result.getContent().stream().map(r -> (String) r[0]).toList();
        Map<String, Long> counts = new HashMap<>();
        if (!quizIds.isEmpty())
            for (Object[] r : questions.countByQuizIds(quizIds))
                counts.put((String) r[0], ((Number) r[1]).longValue());
        var content = result.getContent().stream().map(r -> new QuizSummaryDto((String) r[1], (String) r[2],
                (String) r[3], (String) r[4], counts.getOrDefault((String) r[0], 0L), (Integer) r[5])).toList();
        // The course filter searches courses on demand (/api/admin/courses/search), so the full list
        // (17k+ courses on large data) is no longer sent with every page.
        return new Result(content, result.getTotalElements(), result.getTotalPages(), List.of());
    }
}
