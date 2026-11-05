package com.bidv.coretraining.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bidv.coretraining.quiz.entity.Question;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttemptAnalysisAssemblerTest {
    private static final String SNAPSHOT = """
        [{"id":1,"text":"Q1","options":["A","B"],"correctAnswers":[1]},
         {"id":2,"text":"Q2","options":["A","B","C"],"correctAnswers":[0,2]},
         {"id":3,"text":"Q3","options":["A","B"],"correctAnswers":[0]}]
        """;

    private Question question(long id, Integer... correct) {
        return Question.builder().id(id).text("Q" + id).options(new ArrayList<>(List.of("A", "B", "C")))
                .correctAnswers(new ArrayList<>(List.of(correct))).build();
    }

    private UserQuizScore score(int score, int total, Map<Long, String> sets, Map<Long, Integer> legacy) {
        return UserQuizScore.builder().id(9L).score(score).total(total)
                .selectedAnswerSets(new HashMap<>(sets)).selectedAnswers(new HashMap<>(legacy)).build();
    }

    @Test void snapshotClassifiesCorrectIncorrectAndUnansweredIncludingMultipleChoice() {
        var result = AttemptAnalysisAssembler.fromSnapshot(9L, 1, 3, SNAPSHOT, "{\"1\":[0],\"2\":[2,0]}").orElseThrow();

        assertThat(result.available()).isTrue();
        assertThat(result.source()).isEqualTo(AttemptAnalysisAssembler.SOURCE_SNAPSHOT);
        assertThat(List.of(result.correct(), result.incorrect(), result.unanswered())).containsExactly(1, 1, 1);
        assertThat(result.questions().get(0).correct()).isFalse();
        assertThat(result.questions().get(1).selectedAnswers()).containsExactly(0, 2);
        assertThat(result.questions().get(1).correct()).isTrue();
        assertThat(result.questions().get(2).answered()).isFalse();
    }

    @Test void partialMultipleChoiceIsNotCorrect() {
        var result = AttemptAnalysisAssembler.fromSnapshot(9L, 0, 3, SNAPSHOT, "{\"2\":[0]}").orElseThrow();
        assertThat(result.questions().get(1).correct()).isFalse();
        assertThat(result.incorrect()).isEqualTo(1);
    }

    @Test void snapshotThatDisagreesWithRecordedScoreOrCannotBeReadIsRejected() {
        assertThat(AttemptAnalysisAssembler.fromSnapshot(9L, 3, 3, SNAPSHOT, "{}")).isEmpty();
        assertThat(AttemptAnalysisAssembler.fromSnapshot(9L, 0, 3, "not json", "{}")).isEmpty();
        assertThat(AttemptAnalysisAssembler.fromSnapshot(9L, 0, 3, null, "{}")).isEmpty();
    }

    @Test void currentQuestionsAreUsedWhenTheyReproduceTheRecordedScore() {
        var result = AttemptAnalysisAssembler.fromCurrentQuestions(
                score(1, 2, Map.of(1L, "0,2"), Map.of()), List.of(question(1, 0, 2), question(2, 1)));

        assertThat(result.source()).isEqualTo(AttemptAnalysisAssembler.SOURCE_CURRENT);
        assertThat(result.correct()).isEqualTo(1);
        assertThat(result.unanswered()).isEqualTo(1);
    }

    @Test void legacySingleAnswerColumnIsStillRead() {
        var result = AttemptAnalysisAssembler.fromCurrentQuestions(
                score(1, 1, Map.of(), Map.of(1L, 1)), List.of(question(1, 1)));
        assertThat(result.available()).isTrue();
        assertThat(result.questions().get(0).selectedAnswers()).containsExactly(1);
    }

    @Test void attemptWithoutStoredAnswersIsUnavailable() {
        var result = AttemptAnalysisAssembler.fromCurrentQuestions(score(0, 1, Map.of(), Map.of()), List.of(question(1, 1)));
        assertThat(result.available()).isFalse();
        assertThat(result.unavailableReason()).isEqualTo(AttemptAnalysisAssembler.REASON_NO_ANSWERS);
        assertThat(result.questions()).isEmpty();
    }

    @Test void editedQuizIsNeverGradedAgainstTheLearner() {
        var regraded = score(1, 1, Map.of(1L, "1"), Map.of());
        var changedKey = AttemptAnalysisAssembler.fromCurrentQuestions(regraded, List.of(question(1, 0)));
        var replaced = AttemptAnalysisAssembler.fromCurrentQuestions(regraded, List.of(question(2, 1)));
        var resized = AttemptAnalysisAssembler.fromCurrentQuestions(regraded, List.of(question(1, 1), question(2, 1)));

        for (var result : List.of(changedKey, replaced, resized)) {
            assertThat(result.available()).isFalse();
            assertThat(result.unavailableReason()).isEqualTo(AttemptAnalysisAssembler.REASON_QUIZ_CHANGED);
        }
    }
}
