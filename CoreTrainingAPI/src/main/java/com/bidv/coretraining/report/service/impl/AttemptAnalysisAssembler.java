package com.bidv.coretraining.report.service.impl;

import com.bidv.coretraining.quiz.entity.Question;
import com.bidv.coretraining.quiz.entity.UserQuizScore;
import com.bidv.coretraining.report.dto.TrainingReportDto.AttemptAnalysis;
import com.bidv.coretraining.report.dto.TrainingReportDto.AttemptQuestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Rebuilds a per-question view of one recorded quiz result. A stored exam snapshot is exact; the
 * current questions are only used when they still reproduce the recorded score, because grading a
 * learner against an edited quiz would report answers they were never marked on.
 */
final class AttemptAnalysisAssembler {
    static final String SOURCE_SNAPSHOT = "SNAPSHOT";
    static final String SOURCE_CURRENT = "CURRENT";
    static final String REASON_NO_ANSWERS = "NO_ANSWERS";
    static final String REASON_QUIZ_CHANGED = "QUIZ_CHANGED";

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private record SnapshotQuestion(Long id, String text, List<String> options, List<Integer> correctAnswers) {}

    private AttemptAnalysisAssembler() {}

    /** Empty when the snapshot is unreadable or does not reproduce the recorded score. */
    static Optional<AttemptAnalysis> fromSnapshot(long attemptId, int score, int total, String questionsJson,
            String answersJson) {
        if (questionsJson == null || answersJson == null)
            return Optional.empty();
        List<SnapshotQuestion> questions;
        Map<Long, List<Integer>> answers;
        try {
            questions = JSON.readValue(questionsJson, new TypeReference<List<SnapshotQuestion>>() {});
            answers = JSON.readValue(answersJson, new TypeReference<Map<Long, List<Integer>>>() {});
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        List<AttemptQuestion> rows = questions.stream()
                .map(q -> question(q.id(), q.text(), q.options(), answers.get(q.id()), q.correctAnswers()))
                .toList();
        return reproduces(rows, score, total)
                ? Optional.of(analysis(attemptId, SOURCE_SNAPSHOT, rows))
                : Optional.empty();
    }

    static AttemptAnalysis fromCurrentQuestions(UserQuizScore score, List<Question> questions) {
        Map<Long, String> sets = score.getSelectedAnswerSets() == null ? Map.of() : score.getSelectedAnswerSets();
        Map<Long, Integer> legacy = score.getSelectedAnswers() == null ? Map.of() : score.getSelectedAnswers();
        if (sets.isEmpty() && legacy.isEmpty())
            return unavailable(score.getId(), REASON_NO_ANSWERS);
        List<AttemptQuestion> rows = questions.stream()
                .map(q -> question(q.getId(), q.getText(), q.getOptions(), stored(sets, legacy, q.getId()),
                        correctAnswers(q)))
                .toList();
        var knownIds = questions.stream().map(Question::getId).collect(java.util.stream.Collectors.toSet());
        boolean sameQuestions = knownIds.containsAll(sets.keySet()) && knownIds.containsAll(legacy.keySet());
        return sameQuestions && reproduces(rows, score.getScore(), score.getTotal())
                ? analysis(score.getId(), SOURCE_CURRENT, rows)
                : unavailable(score.getId(), REASON_QUIZ_CHANGED);
    }

    private static boolean reproduces(List<AttemptQuestion> rows, int score, int total) {
        return rows.size() == total && rows.stream().filter(AttemptQuestion::correct).count() == score;
    }

    private static AttemptQuestion question(Long id, String text, List<String> options, List<Integer> selected,
            List<Integer> correct) {
        List<Integer> chosen = normalize(selected);
        List<Integer> expected = normalize(correct);
        return new AttemptQuestion(id, text, options == null ? List.of() : new ArrayList<>(options), chosen, expected,
                !chosen.isEmpty(), !expected.isEmpty() && chosen.equals(expected));
    }

    private static AttemptAnalysis analysis(long attemptId, String source, List<AttemptQuestion> rows) {
        int correct = (int) rows.stream().filter(AttemptQuestion::correct).count();
        int unanswered = (int) rows.stream().filter(q -> !q.answered()).count();
        return new AttemptAnalysis(attemptId, true, source, null, correct, rows.size() - correct - unanswered,
                unanswered, rows);
    }

    private static AttemptAnalysis unavailable(long attemptId, String reason) {
        return new AttemptAnalysis(attemptId, false, null, reason, 0, 0, 0, List.of());
    }

    private static List<Integer> correctAnswers(Question question) {
        if (question.getCorrectAnswers() != null && !question.getCorrectAnswers().isEmpty())
            return question.getCorrectAnswers();
        return question.getCorrectAnswer() == null ? List.of() : List.of(question.getCorrectAnswer());
    }

    private static List<Integer> stored(Map<Long, String> sets, Map<Long, Integer> legacy, Long questionId) {
        String serialized = sets.get(questionId);
        if (serialized == null) {
            Integer single = legacy.get(questionId);
            return single == null ? List.of() : List.of(single);
        }
        if (serialized.isBlank())
            return List.of();
        try {
            return Arrays.stream(serialized.split(",")).map(Integer::valueOf).toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    private static List<Integer> normalize(List<Integer> answers) {
        return answers == null ? List.of() : answers.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
