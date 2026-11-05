package com.bidv.coretraining.flashcard.service;

import com.bidv.coretraining.course.repository.ChapterRepository;
import com.bidv.coretraining.course.repository.projection.PublishedChapterView;
import com.bidv.coretraining.flashcard.repository.FlashcardRepository;
import com.bidv.coretraining.flashcard.repository.projection.FlashcardLessonCountView;
import com.bidv.coretraining.flashcard.service.impl.FlashcardLearningServiceImpl;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.learner.service.model.LearnableCourse;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.repository.projection.LessonOutlineView;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlashcardLibraryTest {
    final FlashcardRepository flashcards = mock(FlashcardRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final LessonRepository lessons = mock(LessonRepository.class);
    final ChapterRepository chapters = mock(ChapterRepository.class);
    final LearningAccessService access = mock(LearningAccessService.class);
    final FlashcardLearningService service = new FlashcardLearningServiceImpl(flashcards, users, lessons, chapters,
            access);
    final User learner = User.builder().id(1L).username("learner").build();

    private LessonOutlineView lesson(String id, String chapterId) {
        var view = mock(LessonOutlineView.class);
        when(view.getId()).thenReturn(id);
        when(view.getTitle()).thenReturn("Bài " + id);
        when(view.getCourseId()).thenReturn("C1");
        when(view.getChapterId()).thenReturn(chapterId);
        return view;
    }

    private PublishedChapterView chapter(String id) {
        var view = mock(PublishedChapterView.class);
        when(view.getId()).thenReturn(id);
        when(view.getCourseId()).thenReturn("C1");
        return view;
    }

    @BeforeEach void setup() {
        when(users.findByUsername("learner")).thenReturn(Optional.of(learner));
        when(access.learnableCourses(learner)).thenReturn(List.of(new LearnableCourse("C1", "CIF", false)));
        List<PublishedChapterView> order = List.of(chapter("A"), chapter("B"));
        when(chapters.findPublishedOrder(List.of("C1"))).thenReturn(order);
    }

    @Test void listsOnlyUnlockedLessonsWithTheirCounts() {
        var first = lesson("L1", "A");
        var second = lesson("L2", "A");
        var third = lesson("L3", "A");
        var nextChapter = lesson("L4", "B");
        when(lessons.findOutlines(List.of("C1"), false)).thenReturn(List.of(first, second, third, nextChapter));
        // L1 is done, so L2 is the next open lesson; L3 and the next chapter stay locked.
        when(users.findCompletedLessonIdsInCourses(1L, List.of("C1"))).thenReturn(Set.of("L1"));
        var count = mock(FlashcardLessonCountView.class);
        when(count.getLessonId()).thenReturn("L2");
        when(count.getTotal()).thenReturn(5L);
        when(count.getMine()).thenReturn(2L);
        when(flashcards.countByLesson(List.of("C1"), 1L)).thenReturn(List.of(count));

        var library = service.library(null, 30, "learner");

        assertThat(library.courses()).hasSize(1);
        var rows = library.courses().get(0).lessons();
        assertThat(rows).extracting("id").containsExactly("L1", "L2");
        assertThat(rows.get(0).total()).isZero();
        assertThat(rows.get(1).total()).isEqualTo(5);
        assertThat(rows.get(1).mine()).isEqualTo(2);
    }

    @Test void finishedChapterOpensTheNextOne() {
        var first = lesson("L1", "A");
        var nextChapter = lesson("L4", "B");
        when(lessons.findOutlines(List.of("C1"), false)).thenReturn(List.of(first, nextChapter));
        when(users.findCompletedLessonIdsInCourses(1L, List.of("C1"))).thenReturn(Set.of("L1"));
        assertThat(service.library(null, 30, "learner").courses().get(0).lessons()).extracting("id")
                .containsExactly("L1", "L4");
    }

    @Test void emptyChapterKeepsLaterChaptersLockedLikeTheLessonGate() {
        var nextChapter = lesson("L4", "B");
        when(lessons.findOutlines(List.of("C1"), false)).thenReturn(List.of(nextChapter));
        assertThat(service.library(null, 30, "learner").courses()).isEmpty();
    }

    @Test void editorsReadEveryPublishedLesson() {
        when(access.learnableCourses(learner)).thenReturn(List.of(new LearnableCourse("C1", "CIF", true)));
        var first = lesson("L1", "A");
        var last = lesson("L3", "A");
        when(lessons.findOutlines(List.of("C1"), false)).thenReturn(List.of(first, last));
        assertThat(service.library(null, 30, "learner").courses().get(0).lessons()).extracting("id")
                .containsExactly("L1", "L3");
        verify(users, never()).findCompletedLessonIdsInCourses(any(), any());
    }

    private List<LearnableCourse> editorCourses(String... names) {
        return java.util.Arrays.stream(names).map(name -> new LearnableCourse(name, name, true)).toList();
    }

    private void lessonInEach(String... courseIds) {
        when(lessons.findOutlines(any(), eq(false))).thenAnswer(call -> {
            java.util.Collection<String> ids = call.getArgument(0);
            return ids.stream().filter(List.of(courseIds)::contains).map(id -> {
                var view = lesson(id + "L", "A");
                when(view.getCourseId()).thenReturn(id);
                return view;
            }).toList();
        });
    }

    private static List<String> names(com.bidv.coretraining.flashcard.dto.FlashcardDto.Library page) {
        return page.courses().stream().map(course -> course.name()).toList();
    }

    @Test void pagesThroughCoursesByNameWithoutRepeating() {
        when(access.learnableCourses(learner)).thenReturn(editorCourses("C", "A", "D", "B", "E"));
        lessonInEach("A", "B", "C", "D", "E");
        var first = service.library(null, 2, "learner");
        assertThat(names(first)).containsExactly("A", "B");
        assertThat(first.nextCursor()).isEqualTo("B");
        var second = service.library(first.nextCursor(), 2, "learner");
        assertThat(names(second)).containsExactly("C", "D");
        var last = service.library(second.nextCursor(), 2, "learner");
        assertThat(names(last)).containsExactly("E");
        assertThat(last.nextCursor()).isNull();
    }

    @Test void coursesWithoutOpenLessonsDoNotShortenAPageOrEndTheList() {
        when(access.learnableCourses(learner)).thenReturn(editorCourses("A", "B", "C", "D"));
        lessonInEach("A", "D");
        var page = service.library(null, 2, "learner");
        assertThat(names(page)).containsExactly("A", "D");
        assertThat(page.nextCursor()).isNull();
    }

    @Test void pageSizeIsCapped() {
        var many = java.util.stream.IntStream.range(0, 60).mapToObj(i -> String.format("C%02d", i)).toArray(String[]::new);
        when(access.learnableCourses(learner)).thenReturn(editorCourses(many));
        lessonInEach(many);
        assertThat(service.library(null, 1000, "learner").courses()).hasSize(50);
    }

    @Test void onlyTheAskedLessonsAreOpenedAndOnlyTheirCoursesRead() {
        when(access.learnableCourses(learner)).thenReturn(editorCourses("A", "B"));
        when(lessons.findCourseIdsOf(List.of("AL"))).thenReturn(Set.of("A"));
        lessonInEach("A", "B");
        assertThat(service.openLessonIds(List.of("AL"), "learner")).containsExactly("AL");
        verify(lessons).findOutlines(List.of("A"), false);
    }

    @Test void coursesTheLearnerCannotStudyStayOut() {
        when(access.learnableCourses(learner)).thenReturn(List.of());
        assertThat(service.library(null, 30, "learner").courses()).isEmpty();
        verifyNoInteractions(lessons, flashcards);
    }
}
