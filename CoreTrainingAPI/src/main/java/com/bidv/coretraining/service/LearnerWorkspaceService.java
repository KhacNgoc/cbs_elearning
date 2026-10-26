package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CourseDto;
import com.bidv.coretraining.dto.CourseMapper;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LearnerWorkspaceService {
        public record Resume(String courseId, String lessonId, String lessonTitle, String courseName,
                        String contentType,
                        Long documentId, Integer documentPage, Double scrollOffset, Double positionSeconds,
                        LocalDateTime lastAccessedAt) {
        }

        public record LearnerCourse(CourseDto course, String source, boolean mandatory, String learningStatus,
                        int completedCount, int totalCount, int percent, LocalDate dueDate,
                        LocalDateTime lastAccessedAt, Resume resume) {
        }

        public record Attempt(String id, String quizId, String courseId, String lessonId, String title, String domainId,
                        String domainName,
                        Integer revision, Integer score, Integer total, Integer passingScore, boolean passed,
                        LocalDateTime takenAt) {
        }

        public record LegacyScore(String courseId, String lessonId, String title, Integer score, Integer total,
                        LocalDateTime takenAt) {
        }

        public record Workspace(List<LearnerCourse> courses, Resume resume, List<Attempt> attempts,
                        List<LegacyScore> legacyScores) {
        }

        private final AuthorizationService auth;
        private final UserRepository users;
        private final CourseRepository courses;
        private final CourseMapper mapper;
        private final LessonProgressRepository progress;
        private final QuizAttemptRepository attempts;
        private final CourseEnrollmentRequestRepository enrollments;

        public LearnerWorkspaceService(AuthorizationService auth, UserRepository users, CourseRepository courses,
                        CourseMapper mapper,
                        LessonProgressRepository progress, QuizAttemptRepository attempts,
                        CourseEnrollmentRequestRepository enrollments) {
                this.auth = auth;
                this.users = users;
                this.courses = courses;
                this.mapper = mapper;
                this.progress = progress;
                this.attempts = attempts;
                this.enrollments = enrollments;
        }

        public Workspace workspace() {
                // The authentication principal was loaded in the security filter's transaction.
                // Reload its identity here so learning collections belong to this persistence
                // context.
                User user = users.findById(auth.current().getId())
                                .orElseThrow(() -> new com.bidv.coretraining.exception.ResourceNotFoundException(
                                                "User"));
                Map<String, LessonProgress> history = new HashMap<>();
                progress.findByUserId(user.getId()).forEach(p -> history.put(p.getLesson().getId(), p));
                Set<String> pending = new HashSet<>();
                enrollments.findByUserIdAndStatus(user.getId(), EnrollmentStatus.PENDING)
                                .forEach(e -> pending.add(e.getCourse().getId()));
                List<Attempt> records = attempts.findByUserIdOrderByTakenAtDesc(user.getId()).stream()
                                .filter(a -> a.getTotal() != null && a.getTotal() > 0)
                                .filter(a -> AccessPolicy.visible(user, a.getQuiz().getLesson().getCourse()))
                                .map(a -> {
                                        Lesson l = a.getQuiz().getLesson();
                                        Course c = l.getCourse();
                                        return new Attempt(a.getId(), a.getQuiz().getId(), c.getId(), l.getId(),
                                                        a.getQuiz().getTitle() == null ? l.getTitle()
                                                                        : a.getQuiz().getTitle(),
                                                        c.getProgram().getDomain().getId(),
                                                        c.getProgram().getDomain().getName(), a.getRevision(),
                                                        a.getScore(), a.getTotal(), a.getPassingScore(), a.isPassed(),
                                                        a.getTakenAt());
                                }).toList();
                List<LearnerCourse> list = courses.findAll().stream()
                                .filter(c -> LearningPolicy.isLearnableStatus(c.getStatus()) && AccessPolicy.visible(user, c))
                                .map(c -> {
                                        boolean mandatory = AccessPolicy.mandatory(user, c)
                                                        || user.hasGroupMandatoryCourse(c.getId());
                                        boolean registered = user.getRegisteredCourses().stream()
                                                        .anyMatch(x -> x.getId().equals(c.getId()));
                                        boolean enrolled = mandatory || registered;
                                        String source = mandatory ? "ASSIGNED"
                                                        : registered ? "REGISTERED"
                                                                        : pending.contains(c.getId()) ? "PENDING"
                                                                                        : "AVAILABLE";
                                        List<Lesson> lessons = LearningPolicy.publishedChapters(c).stream()
                                                        .flatMap(ch -> LearningPolicy.publishedLessons(ch).stream())
                                                        .toList();
                                        int completed = (int) lessons.stream()
                                                        .filter(l -> LearningPolicy.completed(user, l)).count();
                                        LocalDateTime accessed = lessons.stream().map(l -> history.get(l.getId()))
                                                        .filter(Objects::nonNull).map(LessonProgress::getUpdatedAt)
                                                        .filter(Objects::nonNull).max(Comparator.naturalOrder())
                                                        .orElse(null);
                                        boolean started = accessed != null || completed > 0
                                                        || records.stream()
                                                                        .anyMatch(a -> a.courseId().equals(c.getId()))
                                                        || user.getQuizScores().stream().anyMatch(
                                                                        q -> q.getCourse().getId().equals(c.getId()));
                                        boolean complete = enrolled && LearningPolicy.courseCompleted(user, c);
                                        String status = !enrolled ? source
                                                        : complete ? "COMPLETED"
                                                                        : started ? "IN_PROGRESS" : "NOT_STARTED";
                                        CourseDto dto = mapper.toCourseDto(c);
                                        dto.setLessons(mapper.toLessonDtoList(lessons));
                                        dto.getLessons().forEach(TrainingStructureService::outlineOnly);
                                        Resume target = enrolled ? resume(user, c, lessons, history) : null;
                                        return new LearnerCourse(dto, source, mandatory, status, completed,
                                                        lessons.size(),
                                                        lessons.isEmpty() ? 0
                                                                        : (int) Math.round(completed * 100.0
                                                                                        / lessons.size()),
                                                        user.getCourseDeadlines().getOrDefault(c.getId(),
                                                                        c.getCompletionDeadline()),
                                                        accessed, target);
                                }).toList();
                Resume target = list.stream().filter(c -> c.resume() != null && !"COMPLETED".equals(c.learningStatus()))
                                .sorted(Comparator.comparing(LearnerCourse::lastAccessedAt,
                                                Comparator.nullsLast(Comparator.reverseOrder())))
                                .map(LearnerCourse::resume).findFirst().orElse(null);
                List<LegacyScore> legacy = user.getQuizScores().stream()
                                .filter(s -> s.getTotal() != null && s.getTotal() > 0)
                                .filter(s -> AccessPolicy.visible(user, s.getCourse()))
                                .filter(s -> records.stream().noneMatch(a -> a.courseId().equals(s.getCourse().getId())
                                                && Objects.equals(a.lessonId(),
                                                                s.getLesson() == null ? null : s.getLesson().getId())))
                                .map(s -> new LegacyScore(s.getCourse().getId(),
                                                s.getLesson() == null ? null : s.getLesson().getId(),
                                                s.getLesson() == null ? s.getCourse().getName()
                                                                : s.getLesson().getTitle(),
                                                s.getScore(), s.getTotal(), s.getTakenAt()))
                                .toList();
                return new Workspace(list, target, records, legacy);
        }

        private Resume resume(User user, Course course, List<Lesson> lessons, Map<String, LessonProgress> history) {
                List<Lesson> eligible = lessons.stream().filter(
                                l -> !LearningPolicy.completed(user, l) && LearningPolicy.lessonUnlocked(user, l))
                                .toList();
                Lesson lesson = eligible.stream().filter(l -> history.containsKey(l.getId()))
                                .max(Comparator.comparing(l -> history.get(l.getId()).getUpdatedAt(),
                                                Comparator.nullsFirst(Comparator.naturalOrder())))
                                .orElse(eligible.isEmpty() ? lessons.stream().findFirst().orElse(null)
                                                : eligible.get(0));
                if (lesson == null)
                        return null;
                LessonProgress saved = history.get(lesson.getId());
                String type = saved == null ? null : saved.getContentType();
                Long document = saved == null ? null : saved.getDocumentId();
                if (saved != null && saved.isContentCompleted() && lesson.isQuizRequired()
                                && !LearningPolicy.quizPassed(user, lesson))
                        type = "QUIZ";
                else if ("DOCUMENT".equals(type) && lesson.getDocuments().stream()
                                .noneMatch(d -> Objects.equals(d.getId(), saved.getDocumentId()))) {
                        type = null;
                        document = null;
                } else if ("VIDEO".equals(type) && (lesson.getVideoUrl() == null || lesson.getVideoUrl().isBlank()))
                        type = null;
                if (type == null)
                        type = lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank() ? "VIDEO"
                                        : !lesson.getDocuments().isEmpty() ? "DOCUMENT" : "TEXT";
                if ("DOCUMENT".equals(type) && document == null)
                        document = lesson.getDocuments().get(0).getId();
                return new Resume(course.getId(), lesson.getId(), lesson.getTitle(), course.getName(), type, document,
                                saved == null || saved.getDocumentPage() == null ? 1 : saved.getDocumentPage(),
                                saved == null || saved.getScrollOffset() == null ? 0 : saved.getScrollOffset(),
                                saved == null || saved.getLastPositionSeconds() == null ? 0
                                                : saved.getLastPositionSeconds(),
                                saved == null ? null : saved.getUpdatedAt());
        }

        public Resume resumeCourse(Course course, User user) {
                LearningPolicy.assertCourseAccess(user, course);
                Map<String, LessonProgress> history = new HashMap<>();
                progress.findByUserIdAndCourseId(user.getId(), course.getId()).forEach(p -> history.put(p.getLesson().getId(), p));
                List<Lesson> lessons = LearningPolicy.publishedChapters(course).stream()
                                .flatMap(c -> LearningPolicy.publishedLessons(c).stream()).toList();
                return resume(user, course, lessons, history);
        }
}
