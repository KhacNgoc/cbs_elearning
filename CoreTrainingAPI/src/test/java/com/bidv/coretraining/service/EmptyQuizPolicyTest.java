package com.bidv.coretraining.service;
import com.bidv.coretraining.policy.LearningPolicy;

import com.bidv.coretraining.mapper.CourseMapper;
import com.bidv.coretraining.entity.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.*;

class EmptyQuizPolicyTest {
    @Test void emptyQuizIsNotVisibleAndDoesNotBlockProgress() {
        Lesson lesson = Lesson.builder().id("L1").title("Bài học").build();
        lesson.ensureQuiz();
        assertThat(lesson.hasQuiz()).isFalse();
        assertThat(lesson.isQuizRequired()).isFalse();
        assertThat(Mappers.getMapper(CourseMapper.class).toLessonDto(lesson).getHasQuiz()).isFalse();
        assertThatCode(() -> LearningPolicy.assertRequiredQuiz(User.builder().build(), lesson))
                .doesNotThrowAnyException();
    }

    @Test void firstQuestionActivatesQuizAndLastQuestionRemovalDeactivatesIt() {
        Lesson lesson = Lesson.builder().id("L1").title("Bài học").build();
        lesson.ensureQuiz();
        lesson.getQuiz().getQuestions().add(Question.builder().id(1L).build());
        assertThat(lesson.hasQuiz()).isTrue();
        assertThat(lesson.isQuizRequired()).isTrue();
        assertThat(Mappers.getMapper(CourseMapper.class).toLessonDto(lesson).getHasQuiz()).isTrue();
        lesson.getQuiz().getQuestions().clear();
        assertThat(lesson.hasQuiz()).isFalse();
        assertThat(lesson.isQuizRequired()).isFalse();
    }

    @Test void optionalQuizWithQuestionsStaysOptional() {
        Lesson lesson = Lesson.builder().id("L1").build();
        lesson.ensureQuiz();
        lesson.getQuiz().setRequiredForProgress(false);
        lesson.getQuiz().getQuestions().add(Question.builder().id(1L).build());
        assertThat(lesson.hasQuiz()).isTrue();
        assertThat(lesson.isQuizRequired()).isFalse();
    }
}
