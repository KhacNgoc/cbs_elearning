package com.bidv.coretraining.quiz.service.impl;
import com.bidv.coretraining.quiz.dto.QuizSummaryDto;
import com.bidv.coretraining.quiz.dto.QuizSummaryPageDto;


import com.bidv.coretraining.quiz.dto.QuizSummaryPageDto.*;

import com.bidv.coretraining.access.service.AuthorizationService;
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
        var courses = authorization.editableCourses().stream()
                .sorted(Comparator.comparing(Course::getName).thenComparing(Course::getId))
                .map(c -> new CourseOption(c.getId(), c.getName())).toList();
        var ids = courses.stream().map(CourseOption::id)
                .filter(id -> courseId == null || courseId.isBlank() || id.equals(courseId)).toList();
        if (ids.isEmpty()) return new Result(List.of(), 0, 0, courses);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        // Treat search text literally, including SQL LIKE wildcard characters.
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = questions.findQuizSummaries(ids, pattern,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
        return new Result(result.getContent(), result.getTotalElements(), result.getTotalPages(), courses);
    }
}
