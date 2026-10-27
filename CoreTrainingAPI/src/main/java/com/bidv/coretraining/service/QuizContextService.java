package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.QuizLessonContextDto;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class QuizContextService {
    private final AuthorizationService authorization;
    private final LessonRepository lessons;
    public QuizContextService(AuthorizationService authorization, LessonRepository lessons) {
        this.authorization = authorization;
        this.lessons = lessons;
    }
    public record Context(String id, String name, String domainName, String status,
            Integer quizPassingScore, Map<String, Set<String>> adminActions,
            List<QuizLessonContextDto> lessons) {}

    public List<Context> courses() {
        authorization.requireOperation("exams", "read");
        User user = authorization.current();
        return authorization.editableCourses().stream()
                .filter(c -> !"DELETED".equals(c.getStatus()))
                .sorted(Comparator.comparing(Course::getName).thenComparing(Course::getId))
                .map(c -> metadata(c, user, List.of())).toList();
    }

    public Context context(String courseId, String lessonId) {
        authorization.requireOperation("exams", "read");
        if (lessonId != null && !lessonId.isBlank()) {
            String owner = lessons.findQuizCourseId(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Lesson"));
            if (courseId != null && !courseId.isBlank() && !courseId.equals(owner))
                throw new BadRequestException("Bài học không thuộc khóa học đã chọn");
            courseId = owner;
        } else lessonId = null;
        if (courseId == null || courseId.isBlank())
            throw new BadRequestException("Vui lòng chọn khóa học hoặc bài học");
        Course course = authorization.edit(courseId);
        if ("DELETED".equals(course.getStatus())) throw new ResourceNotFoundException("Course");
        var rows = lessons.findQuizContext(courseId, lessonId);
        if (lessonId != null && rows.isEmpty()) throw new ResourceNotFoundException("Lesson");
        return metadata(course, authorization.current(), rows);
    }

    private Context metadata(Course c, User user, List<QuizLessonContextDto> rows) {
        var actions = java.util.stream.Stream.of("read", "create", "update", "delete")
                .filter(a -> AccessPolicy.allowsCourse(user, "exams:" + a, c))
                .collect(java.util.stream.Collectors.toSet());
        String domain = c.getProgram() == null || c.getProgram().getDomain() == null
                ? null : c.getProgram().getDomain().getName();
        return new Context(c.getId(), c.getName(), domain, c.getStatus(), c.getQuizPassingScore(),
                Map.of("exams", actions), rows);
    }
}
