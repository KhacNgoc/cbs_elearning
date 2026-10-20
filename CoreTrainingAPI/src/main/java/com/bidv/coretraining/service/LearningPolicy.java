package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.BadRequestException;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;

/** Shared gate for lessons, progress, assessment and signed video access. */
public final class LearningPolicy {
    private LearningPolicy() {
    }

    public static boolean completed(User user, Lesson lesson) {
        return user.getCompletedLessons().stream().anyMatch(item -> item.getId().equals(lesson.getId()));
    }

    public static List<Chapter> publishedChapters(Course course) {
        return course.getChapters().stream().filter(c -> "PUBLISHED".equals(c.getStatus()))
                .sorted(java.util.Comparator.comparing(Chapter::getChapterOrder).thenComparing(Chapter::getId))
                .toList();
    }

    public static List<Lesson> publishedLessons(Chapter chapter) {
        return chapter.getLessons().stream().filter(l -> "PUBLISHED".equals(l.getStatus()))
                .sorted(java.util.Comparator.comparing(Lesson::getLessonOrder).thenComparing(Lesson::getId)).toList();
    }

    public static boolean chapterCompleted(User user, Chapter chapter) {
        List<Lesson> lessons = publishedLessons(chapter);
        return !lessons.isEmpty() && lessons.stream().allMatch(l -> completed(user, l));
    }

    public static boolean courseCompleted(User user, Course course) {
        var chapters = publishedChapters(course);
        return !chapters.isEmpty() && chapters.stream().allMatch(ch -> chapterCompleted(user, ch)
                && publishedLessons(ch).stream().allMatch(l -> !l.isQuizRequired() || quizPassed(user, l)));
    }

    public static boolean chapterUnlocked(User user, Chapter chapter) {
        return publishedChapters(chapter.getCourse()).stream()
                .takeWhile(c -> !c.getId().equals(chapter.getId())).allMatch(c -> chapterCompleted(user, c));
    }

    public static boolean lessonUnlocked(User user, Lesson lesson) {
        return chapterUnlocked(user, lesson.getChapter()) && publishedLessons(lesson.getChapter()).stream()
                .takeWhile(l -> !l.getId().equals(lesson.getId())).allMatch(l -> completed(user, l));
    }

    public static void assertCourseAccess(User user, Course course) {
        com.bidv.coretraining.security.AccessPolicy.require(user.activeAccount());
        if (com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, course))
            return;
        com.bidv.coretraining.security.AccessPolicy
                .require(com.bidv.coretraining.security.AccessPolicy.permissions(user).contains("LEARN")
                        && com.bidv.coretraining.security.AccessPolicy.visible(user, course));
        boolean enrolled = com.bidv.coretraining.security.AccessPolicy.mandatory(user, course)
                || user.getRegisteredCourses().stream().anyMatch(c -> c.getId().equals(course.getId()))
                || user.hasGroupMandatoryCourse(course.getId());
        if (!"PUBLISHED".equals(course.getStatus()) || !enrolled)
            throw new AccessDeniedException("Bạn chưa được cấp quyền học khóa này");
    }

    public static void assertLessonAccess(User user, Lesson lesson) {
        com.bidv.coretraining.security.AccessPolicy.require(user.activeAccount());
        if (com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, lesson.getCourse()))
            return;
        assertCourseAccess(user, lesson.getCourse());
        if (lesson.getChapter() == null || !"PUBLISHED".equals(lesson.getChapter().getStatus())
                || !"PUBLISHED".equals(lesson.getStatus()) || !lessonUnlocked(user, lesson))
            throw new AccessDeniedException("Hoàn thành các bài và chương trước để mở bài học này");
    }

    public static boolean quizPassed(User user, Lesson lesson) {
        return user.getQuizScores().stream()
                .filter(s -> s.getLesson() != null && s.getLesson().getId().equals(lesson.getId()))
                .anyMatch(s -> s.getTotal() > 0
                        && Math.round(s.getScore() * 100.0 / s.getTotal()) >= lesson.getQuizPassingScore());
    }

    public static void assertRequiredQuiz(User user, Lesson lesson) {
        if (lesson.isQuizRequired() && !quizPassed(user, lesson))
            throw new BadRequestException(
                    "Bạn cần đạt tối thiểu " + lesson.getQuizPassingScore() + "% bài kiểm tra bắt buộc");
    }

    public static void recordCourseCompletion(User user, Course course) {
        List<Chapter> chapters = publishedChapters(course);
        if (!chapters.isEmpty() && chapters.stream().allMatch(c -> chapterCompleted(user, c)))
            user.getCompletedCourses().putIfAbsent(course.getId(), java.time.LocalDateTime.now());
    }
}
